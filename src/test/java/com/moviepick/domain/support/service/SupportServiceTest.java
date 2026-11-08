package com.moviepick.domain.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.payment.client.PortOneClient;
import com.moviepick.domain.payment.client.PortOnePayment;
import com.moviepick.domain.payment.client.PortOneProperties;
import com.moviepick.domain.support.dto.request.SupportCreateRequest;
import com.moviepick.domain.payment.dto.response.ConfirmResult;
import com.moviepick.domain.support.dto.response.SupportConfirmResponse;
import com.moviepick.domain.support.dto.response.SupportCreateResponse;
import com.moviepick.domain.payment.entity.Payment;
import com.moviepick.domain.payment.entity.PaymentStatus;
import com.moviepick.domain.support.exception.SupportErrorCode;
import com.moviepick.domain.payment.repository.PaymentRepository;
import com.moviepick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final String PAYMENT_ID = "support-test-payment";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MemberService memberService;
    @Mock
    private PortOneClient portOneClient;

    private SupportService supportService;
    private Member owner;

    @BeforeEach
    void setUp() {
        PortOneProperties properties = new PortOneProperties(
                "https://api.portone.io", "test-api-secret", "test-store-id", "test-channel-key", "test-webhook-secret");
        supportService = new SupportService(paymentRepository, memberService, portOneClient, properties);

        owner = Member.createLocal("owner@test.com", "encoded", "owner");
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
    }

    // ===================== 후원 생성 =====================

    @Test
    @DisplayName("허용되지 않은 금액은 거절된다")
    void rejectNotAllowedAmount() {
        assertThatThrownBy(() -> supportService.createSupport(OWNER_ID, new SupportCreateRequest(5_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SupportErrorCode.INVALID_SUPPORT_AMOUNT);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("허용된 금액이면 서버가 만든 paymentId로 CREATED 결제를 저장하고 결제창 정보를 응답한다")
    void createSupport() {
        given(memberService.getActiveMember(OWNER_ID)).willReturn(owner);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        SupportCreateResponse response = supportService.createSupport(OWNER_ID, new SupportCreateRequest(3_000L));

        assertThat(response.paymentId()).startsWith("support-");
        assertThat(response.amount()).isEqualTo(3_000L);
        assertThat(response.orderName()).isEqualTo("MoviePick 개발자 후원 - 커피 1잔");
        assertThat(response.storeId()).isEqualTo("test-store-id");
        assertThat(response.channelKey()).isEqualTo("test-channel-key");
        verify(paymentRepository).save(any(Payment.class));
    }

    // ===================== 결제 확인 =====================

    @Test
    @DisplayName("PortOne 결과가 PAID이고 금액이 같으면 PAID가 된다")
    void confirmPaid() {
        givenStoredPayment(createdPayment(3_000L));
        given(portOneClient.getPayment(PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePayment(PortOnePayment.Status.PAID, 3_000L, "KRW")));
        given(paymentRepository.markPaid(eq(PAYMENT_ID), any(LocalDateTime.class))).willReturn(1);

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.result()).isEqualTo(ConfirmResult.COMPLETED);
        assertThat(response.message()).isEqualTo("완료");
        verify(paymentRepository, never()).markFailed(anyString());
    }

    @Test
    @DisplayName("금액이 다르면 FAILED가 된다")
    void confirmAmountMismatch() {
        givenStoredPayment(createdPayment(3_000L));
        given(portOneClient.getPayment(PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePayment(PortOnePayment.Status.PAID, 100L, "KRW")));
        given(paymentRepository.markFailed(PAYMENT_ID)).willReturn(1);

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.result()).isEqualTo(ConfirmResult.FAILED);
        verify(paymentRepository, never()).markPaid(anyString(), any());
    }

    @Test
    @DisplayName("아직 결제 전이면 CREATED가 유지된다")
    void confirmNotPaidYet() {
        givenStoredPayment(createdPayment(3_000L));
        given(portOneClient.getPayment(PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePayment(PortOnePayment.Status.READY, 3_000L, "KRW")));

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.result()).isEqualTo(ConfirmResult.PENDING);
        verifyNoStatusChange();
    }

    @Test
    @DisplayName("PortOne에 결제 건이 아직 없으면(404) CREATED가 유지된다")
    void confirmPortOnePaymentNotFound() {
        givenStoredPayment(createdPayment(3_000L));
        given(portOneClient.getPayment(PAYMENT_ID)).willReturn(Optional.empty());

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.result()).isEqualTo(ConfirmResult.PENDING);
        verifyNoStatusChange();
    }

    @Test
    @DisplayName("이미 PAID인 주문을 다시 확인해도 상태와 결제 시각이 바뀌지 않는다")
    void confirmAlreadyPaid() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 21, 12, 0);
        Payment payment = createdPayment(3_000L);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
        ReflectionTestUtils.setField(payment, "paidAt", paidAt);
        givenStoredPayment(payment);

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.result()).isEqualTo(ConfirmResult.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
        verify(portOneClient, never()).getPayment(anyString());
        verifyNoStatusChange();
    }

    @Test
    @DisplayName("다른 회원의 결제는 확인할 수 없다")
    void confirmOtherMembersPayment() {
        givenStoredPayment(createdPayment(3_000L));

        assertThatThrownBy(() -> supportService.confirm(OTHER_MEMBER_ID, PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SupportErrorCode.NOT_SUPPORT_OWNER);

        verify(portOneClient, never()).getPayment(anyString());
        verifyNoStatusChange();
    }

    // ===================== 추가 확인 =====================

    @Test
    @DisplayName("존재하지 않는 paymentId는 SUPPORT_NOT_FOUND")
    void confirmNotFound() {
        given(paymentRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.confirm(OWNER_ID, PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SupportErrorCode.SUPPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("PortOne 상태가 FAILED면 CREATED를 유지하고 실패로 응답한다 (같은 paymentId로 재시도 가능)")
    void confirmPortOneFailed() {
        givenStoredPayment(createdPayment(3_000L));
        given(portOneClient.getPayment(PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePayment(PortOnePayment.Status.FAILED, 3_000L, "KRW")));

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.result()).isEqualTo(ConfirmResult.FAILED);
        verifyNoStatusChange();
    }

    @Test
    @DisplayName("PortOne 상태가 CANCELLED면 상태 변경 없이 실패로 응답한다")
    void confirmPortOneCancelled() {
        givenStoredPayment(createdPayment(3_000L));
        given(portOneClient.getPayment(PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePayment(PortOnePayment.Status.CANCELLED, 3_000L, "KRW")));

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.result()).isEqualTo(ConfirmResult.FAILED);
        verifyNoStatusChange();
    }

    @Test
    @DisplayName("우리 상태가 EXPIRED면 PortOne을 호출하지 않고 실패로 응답한다")
    void confirmExpired() {
        Payment payment = createdPayment(3_000L);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.EXPIRED);
        givenStoredPayment(payment);

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(response.result()).isEqualTo(ConfirmResult.FAILED);
        verify(portOneClient, never()).getPayment(anyString());
    }

    @Test
    @DisplayName("markPaid가 0행이면(다른 요청이 먼저 처리) 최신 상태를 다시 읽어 응답한다")
    void confirmPaidRaceLost() {
        Payment stored = createdPayment(3_000L);
        Payment alreadyPaid = createdPayment(3_000L);
        ReflectionTestUtils.setField(alreadyPaid, "status", PaymentStatus.PAID);
        given(paymentRepository.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.of(stored), Optional.of(alreadyPaid));
        given(portOneClient.getPayment(PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePayment(PortOnePayment.Status.PAID, 3_000L, "KRW")));
        given(paymentRepository.markPaid(eq(PAYMENT_ID), any(LocalDateTime.class))).willReturn(0);

        SupportConfirmResponse response = supportService.confirm(OWNER_ID, PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.result()).isEqualTo(ConfirmResult.COMPLETED);
    }

    // ===================== helpers =====================

    private Payment createdPayment(long amount) {
        Payment payment = Payment.createSupport(owner, amount, PAYMENT_ID);
        ReflectionTestUtils.setField(payment, "id", 10L);
        return payment;
    }

    private void givenStoredPayment(Payment payment) {
        given(paymentRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(payment));
    }

    private void verifyNoStatusChange() {
        verify(paymentRepository, never()).markPaid(anyString(), any());
        verify(paymentRepository, never()).markFailed(anyString());
        verify(memberService, never()).getActiveMember(anyLong());
    }
}
