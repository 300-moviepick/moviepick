package com.moviepick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moviepick.domain.support.service.SupportService;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.payment.client.PortOneClient;
import com.moviepick.domain.payment.client.PortOnePayment;
import com.moviepick.domain.payment.client.PortOneProperties;
import com.moviepick.domain.payment.client.webhook.PortOneWebhookEvent;
import com.moviepick.domain.payment.client.webhook.PortOneWebhookReceiver;
import com.moviepick.domain.payment.client.webhook.PortOneWebhookVerifier;
import com.moviepick.domain.payment.dto.response.ConfirmResult;
import com.moviepick.domain.support.dto.response.SupportConfirmResponse;
import com.moviepick.domain.payment.entity.Payment;
import com.moviepick.domain.payment.entity.PaymentStatus;
import com.moviepick.domain.payment.repository.PaymentRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 조건부 UPDATE(H2)로 웹훅·confirm 중복 반영이 없는지 확인한다.
 * 테스트 트랜잭션을 끄고(NOT_SUPPORTED) 운영과 같이 Repository 메서드 단위 트랜잭션으로 커밋되게 한다.
 * PortOne 외부 호출(PortOneClient)만 mock.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JpaAuditingConfig.class,
        SupportService.class,
        SupportWebhookService.class,
        PortOneWebhookReceiver.class,
        PortOneWebhookVerifier.class,
        SupportWebhookIdempotencyTest.Config.class
})
class SupportWebhookIdempotencyTest {

    @TestConfiguration
    static class Config {

        @Bean
        PortOneProperties portOneProperties() {
            return new PortOneProperties(
                    "https://api.portone.io", "test-api-secret", "test-store-id", "test-channel-key", "unused");
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private SupportWebhookService supportWebhookService;
    @Autowired
    private SupportService supportService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private PortOneClient portOneClient;
    @MockitoBean
    private MemberService memberService;

    private Long memberId;
    private String paymentId;
    private PortOneWebhookEvent paidEvent;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = memberRepository.saveAndFlush(
                Member.createLocal("webhook-" + suffix + "@test.com", "encoded", "wh" + suffix));
        memberId = member.getId();
        paymentId = "support-" + UUID.randomUUID();
        paymentRepository.saveAndFlush(Payment.createSupport(member, 3_000L, paymentId));
        paidEvent = new PortOneWebhookEvent("msg_" + suffix, "Transaction.Paid", paymentId);
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 웹훅이 2번 와도 PAID 반영은 1번, paidAt은 처음 값 그대로")
    void sameWebhookTwice() {
        givenPortOne(3_000L);

        SupportWebhookResult first = supportWebhookService.handle(paidEvent);
        LocalDateTime firstPaidAt = stored().getPaidAt();
        SupportWebhookResult second = supportWebhookService.handle(paidEvent);

        assertThat(first).isEqualTo(SupportWebhookResult.PAID_APPLIED);
        assertThat(second).isEqualTo(SupportWebhookResult.ALREADY_PROCESSED);
        assertThat(stored().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(firstPaidAt).isNotNull();
        assertThat(stored().getPaidAt()).isEqualTo(firstPaidAt);
        verify(portOneClient, times(1)).getPayment(paymentId);   // 두 번째는 PortOne을 부르지 않는다
    }

    @Test
    @DisplayName("confirm으로 PAID가 된 뒤 웹훅이 오면 중복 반영이 없다")
    void confirmThenWebhook() {
        givenPortOne(3_000L);

        SupportConfirmResponse confirmed = supportService.confirm(memberId, paymentId);
        LocalDateTime paidAt = stored().getPaidAt();
        SupportWebhookResult webhook = supportWebhookService.handle(paidEvent);

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(webhook).isEqualTo(SupportWebhookResult.ALREADY_PROCESSED);
        assertThat(stored().getPaidAt()).isEqualTo(paidAt);
        verify(portOneClient, times(1)).getPayment(paymentId);
    }

    @Test
    @DisplayName("웹훅으로 PAID가 된 뒤 confirm은 COMPLETED로 응답하고 상태는 그대로")
    void webhookThenConfirm() {
        givenPortOne(3_000L);

        supportWebhookService.handle(paidEvent);
        LocalDateTime paidAt = stored().getPaidAt();
        SupportConfirmResponse confirmed = supportService.confirm(memberId, paymentId);

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(confirmed.result()).isEqualTo(ConfirmResult.COMPLETED);
        assertThat(stored().getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("이미 PAID인 결제는 재조회 금액이 달라도 웹훅으로 FAILED가 되지 않는다")
    void paidNotChangedToFailed() {
        paymentRepository.markPaid(paymentId, LocalDateTime.of(2026, 11, 6, 10, 0));
        givenPortOne(100L);

        SupportWebhookResult result = supportWebhookService.handle(paidEvent);

        assertThat(result).isEqualTo(SupportWebhookResult.ALREADY_PROCESSED);
        assertThat(stored().getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(portOneClient, never()).getPayment(anyString());
    }

    @Test
    @DisplayName("금액이 다르면 FAILED로 커밋되고 PAID가 되지 않는다")
    void amountMismatchCommitted() {
        givenPortOne(100L);

        assertThat(supportWebhookService.handle(paidEvent)).isEqualTo(SupportWebhookResult.AMOUNT_MISMATCH);
        assertThat(stored().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stored().getPaidAt()).isNull();
    }

    private void givenPortOne(long amount) {
        given(portOneClient.getPayment(paymentId))
                .willReturn(Optional.of(new PortOnePayment(PortOnePayment.Status.PAID, amount, "KRW")));
    }

    // 별도 조회 (바깥 트랜잭션이 없으므로 커밋된 값을 읽는다)
    private Payment stored() {
        return paymentRepository.findByPaymentId(paymentId).orElseThrow();
    }
}
