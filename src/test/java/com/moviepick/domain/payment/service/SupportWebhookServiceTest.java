package com.moviepick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moviepick.domain.support.service.SupportService;
import com.moviepick.domain.payment.exception.PaymentErrorCode;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.payment.client.PortOneClient;
import com.moviepick.domain.payment.client.PortOnePayment;
import com.moviepick.domain.payment.client.PortOneProperties;
import com.moviepick.domain.payment.client.webhook.PortOneWebhookEvent;
import com.moviepick.domain.payment.client.webhook.PortOneWebhookReceiver;
import com.moviepick.domain.payment.entity.Payment;
import com.moviepick.domain.payment.entity.PaymentStatus;
import com.moviepick.domain.payment.repository.PaymentRepository;
import com.moviepick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 결제 웹훅 처리 흐름(후원 결제 기준). SupportService는 실제 객체로 두어 confirm 로직 재사용을 함께 확인한다. */
@ExtendWith(MockitoExtension.class)
class SupportWebhookServiceTest {

    private static final String PAYMENT_ID = "support-webhook-test";
    private static final PortOneWebhookEvent PAID_EVENT =
            new PortOneWebhookEvent("msg_1", "Transaction.Paid", PAYMENT_ID);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MemberService memberService;
    @Mock
    private PortOneClient portOneClient;
    @Mock
    private PortOneWebhookReceiver webhookReceiver;

    private SupportWebhookService supportWebhookService;
    private Member owner;

    @BeforeEach
    void setUp() {
        PortOneProperties properties = new PortOneProperties(
                "https://api.portone.io", "test-api-secret", "test-store-id", "test-channel-key", "test-webhook-secret");
        SupportService supportService = new SupportService(paymentRepository, memberService, portOneClient, properties);
        supportWebhookService = new SupportWebhookService(paymentRepository, webhookReceiver, List.of(supportService));

        owner = Member.createLocal("owner@test.com", "encoded", "owner");
        ReflectionTestUtils.setField(owner, "id", 1L);
    }

    @Test
    @DisplayName("재조회 결과가 결제 완료이고 금액이 같으면 markPaid로 PAID 반영")
    void paid() {
        givenStored(payment(PaymentStatus.CREATED));
        givenPortOne(PortOnePayment.Status.PAID, 3_000L, "KRW");
        given(paymentRepository.markPaid(eq(PAYMENT_ID), any(LocalDateTime.class))).willReturn(1);

        assertThat(supportWebhookService.handle(PAID_EVENT)).isEqualTo(SupportWebhookResult.PAID_APPLIED);

        verify(paymentRepository, times(1)).markPaid(eq(PAYMENT_ID), any(LocalDateTime.class));
        verify(paymentRepository, never()).markFailed(anyString());
        verifyNoInteractions(portOneClient);   // 재조회는 공통 컴포넌트(webhookReceiver)로만 한다
    }

    @Test
    @DisplayName("금액이 다르면 PAID 반영 없이 markFailed")
    void amountMismatch() {
        givenStored(payment(PaymentStatus.CREATED));
        givenPortOne(PortOnePayment.Status.PAID, 100L, "KRW");
        given(paymentRepository.markFailed(PAYMENT_ID)).willReturn(1);

        assertThat(supportWebhookService.handle(PAID_EVENT)).isEqualTo(SupportWebhookResult.AMOUNT_MISMATCH);

        verify(paymentRepository, never()).markPaid(anyString(), any());
        verify(paymentRepository).markFailed(PAYMENT_ID);
    }

    @Test
    @DisplayName("통화가 다르면 PAID 반영 없이 markFailed")
    void currencyMismatch() {
        givenStored(payment(PaymentStatus.CREATED));
        givenPortOne(PortOnePayment.Status.PAID, 3_000L, "USD");
        given(paymentRepository.markFailed(PAYMENT_ID)).willReturn(1);

        assertThat(supportWebhookService.handle(PAID_EVENT)).isEqualTo(SupportWebhookResult.AMOUNT_MISMATCH);

        verify(paymentRepository, never()).markPaid(anyString(), any());
    }

    @ParameterizedTest(name = "PortOne 상태 {0}")
    @EnumSource(value = PortOnePayment.Status.class, names = {"READY", "PAY_PENDING", "FAILED", "CANCELLED", "UNKNOWN"})
    @DisplayName("웹훅이 결제 완료라고 해도 재조회 결과가 결제 완료가 아니면 상태를 바꾸지 않는다")
    void portOneNotPaid(PortOnePayment.Status status) {
        givenStored(payment(PaymentStatus.CREATED));
        givenPortOne(status, 3_000L, "KRW");

        assertThat(supportWebhookService.handle(PAID_EVENT)).isEqualTo(SupportWebhookResult.NOT_PAID);

        verifyNoStatusChange();
    }

    @Test
    @DisplayName("존재하지 않는 결제는 NOT_FOUND, PortOne을 부르지 않는다")
    void notFound() {
        given(paymentRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());

        assertThat(supportWebhookService.handle(PAID_EVENT)).isEqualTo(SupportWebhookResult.NOT_FOUND);

        verifyNoInteractions(webhookReceiver);
        verifyNoStatusChange();
    }

    @Test
    @DisplayName("이미 PAID인 결제는 PortOne을 부르지 않고 FAILED로도 바꾸지 않는다")
    void alreadyPaid() {
        givenStored(payment(PaymentStatus.PAID));

        assertThat(supportWebhookService.handle(PAID_EVENT)).isEqualTo(SupportWebhookResult.ALREADY_PROCESSED);

        verifyNoInteractions(webhookReceiver);
        verifyNoStatusChange();
    }

    @ParameterizedTest(name = "현재 상태 {0}")
    @EnumSource(value = PaymentStatus.class, names = {"EXPIRED", "FAILED", "CANCELLED"})
    @DisplayName("종료 상태 결제에 결제 완료 웹훅이 오면 상태를 바꾸지 않고 ALREADY_PROCESSED (수동 확인 warn 로그)")
    void terminalStatusWithPaidEvent(PaymentStatus status) {
        givenStored(payment(status));

        assertThat(supportWebhookService.handle(PAID_EVENT)).isEqualTo(SupportWebhookResult.ALREADY_PROCESSED);

        verifyNoInteractions(webhookReceiver);
        verifyNoStatusChange();
    }

    @Test
    @DisplayName("경합: 읽을 때 CREATED였지만 그사이 PAID가 됐으면 markFailed는 0행이고 FAILED가 되지 않는다")
    void paidConcurrentlyNotOverwrittenByFailed() {
        Payment paidMeanwhile = payment(PaymentStatus.PAID);
        given(paymentRepository.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.of(payment(PaymentStatus.CREATED)), Optional.of(paidMeanwhile));
        givenPortOne(PortOnePayment.Status.PAID, 100L, "KRW");
        given(paymentRepository.markFailed(PAYMENT_ID)).willReturn(0);

        SupportWebhookResult result = supportWebhookService.handle(PAID_EVENT);

        assertThat(result).isNotEqualTo(SupportWebhookResult.AMOUNT_MISMATCH);
        assertThat(result).isEqualTo(SupportWebhookResult.PAID_APPLIED);
    }

    @Test
    @DisplayName("경합: markPaid가 0행이면(다른 요청이 먼저 반영) 최신 상태 PAID로 응답한다")
    void markPaidZeroRows() {
        given(paymentRepository.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.of(payment(PaymentStatus.CREATED)), Optional.of(payment(PaymentStatus.PAID)));
        givenPortOne(PortOnePayment.Status.PAID, 3_000L, "KRW");
        given(paymentRepository.markPaid(eq(PAYMENT_ID), any(LocalDateTime.class))).willReturn(0);

        assertThat(supportWebhookService.handle(PAID_EVENT)).isEqualTo(SupportWebhookResult.PAID_APPLIED);
        verify(paymentRepository, never()).markFailed(anyString());
    }

    @Test
    @DisplayName("처리 대상이 아닌 이벤트는 DB·PortOne을 건드리지 않고 무시한다")
    void ignoredEventType() {
        PortOneWebhookEvent cancelled = new PortOneWebhookEvent("msg_2", "Transaction.Cancelled", PAYMENT_ID);

        assertThat(supportWebhookService.handle(cancelled)).isEqualTo(SupportWebhookResult.IGNORED);

        verifyNoInteractions(paymentRepository, webhookReceiver);
    }

    @Test
    @DisplayName("PortOne에 결제 건이 없으면 PORTONE_REQUEST_FAILED(5xx)로 재시도를 유도한다")
    void portOnePaymentMissing() {
        givenStored(payment(PaymentStatus.CREATED));
        given(webhookReceiver.fetchPayment(PAYMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportWebhookService.handle(PAID_EVENT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PORTONE_REQUEST_FAILED);
        verifyNoStatusChange();
    }

    @Test
    @DisplayName("PortOne 재조회 타임아웃은 그대로 전파된다 (504로 응답해 재시도 유도)")
    void portOneTimeout() {
        givenStored(payment(PaymentStatus.CREATED));
        given(webhookReceiver.fetchPayment(PAYMENT_ID))
                .willThrow(new BusinessException(PaymentErrorCode.PORTONE_TIMEOUT));

        assertThatThrownBy(() -> supportWebhookService.handle(PAID_EVENT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PORTONE_TIMEOUT);
        verifyNoStatusChange();
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = Payment.createSupport(owner, 3_000L, PAYMENT_ID);
        ReflectionTestUtils.setField(payment, "id", 10L);
        ReflectionTestUtils.setField(payment, "status", status);
        return payment;
    }

    private void givenStored(Payment payment) {
        given(paymentRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(payment));
    }

    private void givenPortOne(PortOnePayment.Status status, Long amount, String currency) {
        given(webhookReceiver.fetchPayment(PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePayment(status, amount, currency)));
    }

    private void verifyNoStatusChange() {
        verify(paymentRepository, never()).markPaid(anyString(), any());
        verify(paymentRepository, never()).markFailed(anyString());
    }
}
