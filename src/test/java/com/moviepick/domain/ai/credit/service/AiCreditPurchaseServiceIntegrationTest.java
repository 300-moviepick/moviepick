package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.exception.AiErrorCode;
import com.moviepick.domain.ai.credit.dto.request.AiCreditPurchaseRequest;
import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
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
import com.moviepick.domain.payment.entity.Payment;
import com.moviepick.domain.payment.entity.PaymentStatus;
import com.moviepick.domain.payment.entity.ProductType;
import com.moviepick.domain.support.exception.SupportErrorCode;
import com.moviepick.domain.payment.repository.PaymentRepository;
import com.moviepick.domain.support.service.SupportService;
import com.moviepick.domain.payment.service.SupportWebhookResult;
import com.moviepick.domain.payment.service.SupportWebhookService;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AI 크레딧 구매 생성·확정·웹훅 지급. 실제 커밋으로 검증하고 PortOne 외부 호출(PortOneClient)만 mock.
 * spring.ai.model.chat을 지정하지 않으므로(기본 none) "AI가 꺼져 있어도 지급된다"도 함께 확인한다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JpaAuditingConfig.class,
        AiCreditPurchaseService.class,
        AiCreditPurchaseGrantService.class,
        AiCreditWalletProvisioner.class,
        AiSignupCreditService.class,
        AiCreditQueryService.class,
        AiCreditSpendService.class,
        SupportService.class,
        SupportWebhookService.class,
        PortOneWebhookReceiver.class,
        PortOneWebhookVerifier.class,
        AiCreditPurchaseServiceIntegrationTest.Config.class
})
class AiCreditPurchaseServiceIntegrationTest {

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
    private AiCreditPurchaseService purchaseService;
    @Autowired
    private SupportService supportService;
    @Autowired
    private SupportWebhookService webhookService;
    @Autowired
    private AiCreditSpendService spendService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private AiCreditWalletRepository walletRepository;
    @Autowired
    private AiCreditTransactionRepository transactionRepository;
    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private PortOneClient portOneClient;
    @MockitoBean
    private MemberService memberService;

    private Member member;
    private Member other;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        member = memberRepository.saveAndFlush(
                Member.createLocal("buyer-" + suffix + "@test.com", "encoded", "buyer" + suffix));
        other = memberRepository.saveAndFlush(
                Member.createLocal("other-" + suffix + "@test.com", "encoded", "other" + suffix));
        given(memberService.getActiveMember(member.getId())).willReturn(member);
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    // ===================== 구매 생성 =====================

    @Test
    void 구매_생성은_서버_가격표의_금액과_주문명을_쓴다() {
        var response = purchaseService.createPurchase(
                member.getId(), new AiCreditPurchaseRequest("CREDIT_30"));

        assertThat(response.paymentId()).startsWith("credit-");
        assertThat(response.amount()).isEqualTo(2_500L);
        assertThat(response.orderName()).isEqualTo("MoviePick AI 크레딧 30회");
        assertThat(response.storeId()).isEqualTo("test-store-id");
        assertThat(response.channelKey()).isEqualTo("test-channel-key");

        Payment saved = paymentRepository.findByPaymentId(response.paymentId()).orElseThrow();
        assertThat(saved.getProductType()).isEqualTo(ProductType.AI_CREDIT);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(saved.getAmount()).isEqualTo(2_500L);
    }

    @Test
    void 알_수_없는_상품_코드는_거부한다() {
        assertThatThrownBy(() -> purchaseService.createPurchase(
                member.getId(), new AiCreditPurchaseRequest("CREDIT_99")))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_CREDIT_PRODUCT_NOT_FOUND));

        assertThat(paymentRepository.count()).isZero();
    }

    // ===================== 구매 확정 =====================

    @Test
    void 결제_완료면_유료_크레딧을_지급하고_PURCHASE_원장을_남긴다() {
        String paymentId = create("CREDIT_10");
        givenPortOne(paymentId, PortOnePayment.Status.PAID, 1_000L, "KRW");

        var response = purchaseService.confirm(member.getId(), paymentId);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.result()).isEqualTo(ConfirmResult.COMPLETED);
        // 지갑이 없던 회원이라 무료 3회 지갑이 먼저 생기고, 유료 10회가 더해진다
        assertThat(response.credits().paidAvailable()).isEqualTo(10);
        assertThat(response.credits().freeAvailable()).isEqualTo(3);

        List<AiCreditTransaction> purchases = purchases();
        assertThat(purchases).hasSize(1);
        assertThat(purchases.get(0).getDelta()).isEqualTo(10);
        assertThat(purchases.get(0).getBucket()).isEqualTo(AiCreditBucket.PAID);
        assertThat(purchases.get(0).getIdempotencyKey()).isEqualTo("PURCHASE:" + paymentId);
        assertThat(purchases.get(0).getPayment().getId())
                .isEqualTo(paymentRepository.findByPaymentId(paymentId).orElseThrow().getId());
    }

    @Test
    void 금액이_다르면_지급하지_않고_FAILED() {
        String paymentId = create("CREDIT_10");
        givenPortOne(paymentId, PortOnePayment.Status.PAID, 900L, "KRW");

        var response = purchaseService.confirm(member.getId(), paymentId);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.credits().paidAvailable()).isZero();
        assertThat(purchases()).isEmpty();
    }

    @Test
    void 통화가_다르면_지급하지_않고_FAILED() {
        String paymentId = create("CREDIT_10");
        givenPortOne(paymentId, PortOnePayment.Status.PAID, 1_000L, "USD");

        var response = purchaseService.confirm(member.getId(), paymentId);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.credits().paidAvailable()).isZero();
        assertThat(purchases()).isEmpty();
    }

    @Test
    void 결제_미완료면_지급하지_않고_확인_중() {
        String paymentId = create("CREDIT_10");
        givenPortOne(paymentId, PortOnePayment.Status.READY, 1_000L, "KRW");

        var response = purchaseService.confirm(member.getId(), paymentId);

        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.result()).isEqualTo(ConfirmResult.PENDING);
        assertThat(purchases()).isEmpty();
    }

    @Test
    void 다른_회원의_결제는_거부한다() {
        String paymentId = create("CREDIT_10");

        assertThatThrownBy(() -> purchaseService.confirm(other.getId(), paymentId))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.NOT_AI_CREDIT_PURCHASE_OWNER));

        assertThat(purchases()).isEmpty();
    }

    @Test
    void 같은_confirm을_두_번_보내도_1회만_지급한다() {
        String paymentId = create("CREDIT_10");
        givenPortOne(paymentId, PortOnePayment.Status.PAID, 1_000L, "KRW");

        purchaseService.confirm(member.getId(), paymentId);
        var second = purchaseService.confirm(member.getId(), paymentId);

        assertThat(second.result()).isEqualTo(ConfirmResult.COMPLETED);
        assertThat(second.credits().paidAvailable()).isEqualTo(10);
        assertThat(purchases()).hasSize(1);
        verify(portOneClient, times(1)).getPayment(paymentId);   // 두 번째는 PortOne을 부르지 않는다
    }

    @Test
    void 후원_결제와_크레딧_결제는_서로의_confirm으로_처리되지_않는다() {
        String supportPaymentId = "support-" + UUID.randomUUID();
        paymentRepository.saveAndFlush(Payment.createSupport(member, 3_000L, supportPaymentId));
        String creditPaymentId = create("CREDIT_10");

        assertThatThrownBy(() -> purchaseService.confirm(member.getId(), supportPaymentId))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_CREDIT_PURCHASE_NOT_FOUND));

        assertThatThrownBy(() -> supportService.confirm(member.getId(), creditPaymentId))
                .satisfies(e -> assertErrorCode(e, SupportErrorCode.SUPPORT_NOT_FOUND));

        assertThat(paymentRepository.findByPaymentId(creditPaymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CREATED);
    }

    // ===================== 웹훅 =====================

    @Test
    void 크레딧_결제_웹훅으로_지급하고_이후_confirm은_다시_지급하지_않는다() {
        String paymentId = create("CREDIT_50");
        givenPortOne(paymentId, PortOnePayment.Status.PAID, 4_000L, "KRW");

        SupportWebhookResult result = webhookService.handle(paidEvent(paymentId));
        var confirmed = purchaseService.confirm(member.getId(), paymentId);

        assertThat(result).isEqualTo(SupportWebhookResult.PAID_APPLIED);
        assertThat(confirmed.result()).isEqualTo(ConfirmResult.COMPLETED);
        assertThat(confirmed.credits().paidAvailable()).isEqualTo(50);
        assertThat(purchases()).hasSize(1);
    }

    @Test
    void 후원_결제_웹훅은_크레딧을_지급하지_않고_PAID만_반영한다() {
        String paymentId = "support-" + UUID.randomUUID();
        paymentRepository.saveAndFlush(Payment.createSupport(member, 3_000L, paymentId));
        givenPortOne(paymentId, PortOnePayment.Status.PAID, 3_000L, "KRW");

        SupportWebhookResult result = webhookService.handle(paidEvent(paymentId));

        assertThat(result).isEqualTo(SupportWebhookResult.PAID_APPLIED);
        assertThat(paymentRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(purchases()).isEmpty();
        assertThat(walletRepository.findByMember_Id(member.getId())).isEmpty();
    }

    // ===================== 유료 크레딧 사용 =====================

    @Test
    void 구매한_유료_크레딧은_무료를_다_쓴_뒤에_차감된다() {
        String paymentId = create("CREDIT_10");
        givenPortOne(paymentId, PortOnePayment.Status.PAID, 1_000L, "KRW");
        purchaseService.confirm(member.getId(), paymentId);   // 무료 3 + 유료 10

        for (int i = 0; i < 3; i++) {
            assertThat(spend().bucket()).isEqualTo(AiCreditBucket.FREE);
        }
        var fourth = spend();

        assertThat(fourth.bucket()).isEqualTo(AiCreditBucket.PAID);
        AiCreditWallet wallet = walletRepository.findByMember_Id(member.getId()).orElseThrow();
        assertThat(wallet.getFreeAvailable()).isZero();
        assertThat(wallet.getPaidAvailable()).isEqualTo(9);
    }

    // ===================== 도우미 =====================

    private String create(String productCode) {
        return purchaseService.createPurchase(
                member.getId(), new AiCreditPurchaseRequest(productCode)).paymentId();
    }

    private void givenPortOne(String paymentId, PortOnePayment.Status status, long amount, String currency) {
        given(portOneClient.getPayment(paymentId))
                .willReturn(Optional.of(new PortOnePayment(status, amount, currency)));
    }

    private PortOneWebhookEvent paidEvent(String paymentId) {
        return new PortOneWebhookEvent("msg_" + UUID.randomUUID(), "Transaction.Paid", paymentId);
    }

    private AiCreditSpendService.SpendResult spend() {
        return spendService.spendOne(member.getId(), UUID.randomUUID().toString());
    }

    private List<AiCreditTransaction> purchases() {
        return transactionRepository.findAll().stream()
                .filter(tx -> tx.getEventType() == AiCreditEventType.PURCHASE)
                .toList();
    }

    private static void assertErrorCode(Throwable e, ErrorCode expected) {
        assertThat(e).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }
}
