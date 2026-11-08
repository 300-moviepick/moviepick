package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.dto.request.AiCreditPurchaseRequest;
import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
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
import com.moviepick.domain.payment.entity.PaymentStatus;
import com.moviepick.domain.payment.repository.PaymentRepository;
import com.moviepick.domain.support.service.SupportService;
import com.moviepick.domain.payment.service.SupportWebhookResult;
import com.moviepick.domain.payment.service.SupportWebhookService;
import com.moviepick.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

// 같은 크레딧 결제에 confirm 2개와 웹훅 2개가 동시에 와도 지급은 1회, PURCHASE 원장 1건인지 확인한다.
// 이 테스트만 전용 H2 DB에 LOCK_TIMEOUT 10초를 준다 (②-1 동시 차감 테스트와 같은 방식).
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-credit-purchase-concurrency;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JpaAuditingConfig.class,
        AiCreditPurchaseService.class,
        AiCreditPurchaseGrantService.class,
        AiCreditWalletProvisioner.class,
        AiSignupCreditService.class,
        AiCreditQueryService.class,
        SupportService.class,
        SupportWebhookService.class,
        PortOneWebhookReceiver.class,
        PortOneWebhookVerifier.class,
        AiCreditPurchaseConcurrencyTest.Config.class
})
class AiCreditPurchaseConcurrencyTest {

    private static final int CREDITS = 10;

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
    private SupportWebhookService webhookService;
    @Autowired
    private AiCreditWalletProvisioner walletProvisioner;
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

    private Long memberId;
    private String paymentId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.saveAndFlush(
                Member.createLocal("race@test.com", "encoded", "경합구매"));
        memberId = member.getId();
        given(memberService.getActiveMember(memberId)).willReturn(member);

        // 지갑은 미리 만든다 (ensureWallet 동시 생성은 ②-1 테스트가 따로 확인)
        walletProvisioner.ensureWallet(memberId);
        paymentId = purchaseService.createPurchase(
                memberId, new AiCreditPurchaseRequest("CREDIT_10")).paymentId();

        // 모든 스레드가 CREATED 확인을 통과한 뒤 지급 단계에서 만나도록 재조회를 조금 늦춘다
        given(portOneClient.getPayment(anyString())).willAnswer(invocation -> {
            Thread.sleep(200);
            return Optional.of(new PortOnePayment(PortOnePayment.Status.PAID, 1_000L, "KRW"));
        });
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void confirm과_웹훅이_동시에_와도_지급은_1회() throws Exception {

        List<Callable<Outcome>> tasks = List.of(
                this::confirm, this::confirm, this::webhook, this::webhook
        );

        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();

        for (Callable<Outcome> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }

        start.countDown();

        int observedPaid = 0;
        int lockError = 0;
        for (Future<Outcome> future : futures) {
            switch (future.get(60, TimeUnit.SECONDS)) {
                case PAID -> observedPaid++;
                case LOCK_ERROR -> lockError++;
            }
        }
        pool.shutdown();

        long purchaseCount = transactionRepository.findAll().stream()
                .filter(tx -> tx.getEventType() == AiCreditEventType.PURCHASE)
                .count();
        int paid = walletRepository.findByMember_Id(memberId).orElseThrow().getPaidAvailable();

        // 결과 문서 기록용
        System.out.printf(
                "[AiCreditPurchaseConcurrencyTest] observedPaid=%d, lockError=%d, purchase=%d, paid=%d%n",
                observedPaid, lockError, purchaseCount, paid
        );

        assertThat(purchaseCount).isEqualTo(1);
        assertThat(paid).isEqualTo(CREDITS);
        assertThat(paymentRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
        // 성공(PAID를 본 요청) 0건으로는 통과하지 않는다
        assertThat(observedPaid).isGreaterThanOrEqualTo(1);
    }

    private Outcome confirm() {
        try {
            var response = purchaseService.confirm(memberId, paymentId);
            return response.result() == ConfirmResult.COMPLETED ? Outcome.PAID : Outcome.OTHER;
        } catch (DataAccessException e) {
            return Outcome.LOCK_ERROR;
        }
    }

    private Outcome webhook() {
        try {
            SupportWebhookResult result = webhookService.handle(
                    new PortOneWebhookEvent("msg_" + UUID.randomUUID(), "Transaction.Paid", paymentId));
            return result == SupportWebhookResult.PAID_APPLIED
                    || result == SupportWebhookResult.ALREADY_PROCESSED
                    ? Outcome.PAID : Outcome.OTHER;
        } catch (DataAccessException e) {
            return Outcome.LOCK_ERROR;
        }
    }

    private enum Outcome {
        PAID, OTHER, LOCK_ERROR
    }
}
