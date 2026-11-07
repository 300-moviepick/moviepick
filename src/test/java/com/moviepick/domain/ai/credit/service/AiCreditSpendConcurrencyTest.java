package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 회원이 서로 다른 요청 키로 동시에 크레딧을 쓸 때 이중 차감·음수가 없는지 확인한다.
// H2 기본 잠금 대기(1초)로는 대기 스레드가 시간 초과로 실패할 수 있어, 이 테스트만 전용 H2 DB에 LOCK_TIMEOUT 10초를 준다.
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-credit-spend-concurrency;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JpaAuditingConfig.class,
        AiCreditSpendService.class
})
class AiCreditSpendConcurrencyTest {

    private static final int INITIAL_FREE = 3;
    private static final int THREADS = 10;

    @Autowired
    private AiCreditSpendService spendService;

    @Autowired
    private AiCreditWalletRepository walletRepository;

    @Autowired
    private AiCreditTransactionRepository transactionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long memberId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member member = memberRepository.saveAndFlush(
                    Member.createLocal(
                            "ai-concurrency@test.com",
                            "encoded-password",
                            "AI동시차감"
                    )
            );
            memberId = member.getId();

            AiCreditWallet wallet =
                    walletRepository.saveAndFlush(new AiCreditWallet(member));
            wallet.grantFreeCredits(INITIAL_FREE);

            transactionRepository.saveAndFlush(
                    AiCreditTransaction.freeGrant(
                            wallet,
                            INITIAL_FREE,
                            "FREE_GRANT:MEMBER:" + memberId
                    )
            );
        });
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 동시_차감은_잔액을_넘지_않고_음수가_되지_않는다() throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return spend();
            }));
        }

        start.countDown();

        int success = 0;
        int insufficient = 0;
        int lockTimeout = 0;

        for (Future<Outcome> future : futures) {
            switch (future.get(60, TimeUnit.SECONDS)) {
                case SUCCESS -> success++;
                case INSUFFICIENT -> insufficient++;
                case LOCK_TIMEOUT -> lockTimeout++;
            }
        }

        pool.shutdown();

        // 결과 문서 기록용
        System.out.printf(
                "[AiCreditSpendConcurrencyTest] success=%d, insufficient=%d, lockTimeout=%d%n",
                success, insufficient, lockTimeout
        );

        AiCreditWallet wallet =
                walletRepository.findByMember_Id(memberId).orElseThrow();

        long useCount = transactionRepository.findAll().stream()
                .filter(tx -> tx.getEventType() == AiCreditEventType.USE)
                .count();

        // 공통: 초과 차감·음수 없음, 잔액과 원장 일치
        assertThat(wallet.getFreeAvailable())
                .isEqualTo(INITIAL_FREE - success)
                .isGreaterThanOrEqualTo(0);
        assertThat(useCount).isEqualTo(success);

        if (lockTimeout == 0) {
            // 잠금 대기가 충분하면 정확히 잔액만큼만 성공한다
            assertThat(success).isEqualTo(INITIAL_FREE);
            assertThat(insufficient).isEqualTo(THREADS - INITIAL_FREE);
        } else {
            // 시간 초과가 생겨도 성공 0건으로 통과하지 않게 최소 1건을 요구한다
            assertThat(success).isBetween(1, INITIAL_FREE);
        }
    }

    private Outcome spend() {
        try {
            spendService.spendOne(memberId, UUID.randomUUID().toString());
            return Outcome.SUCCESS;

        } catch (BusinessException e) {
            if (e.getErrorCode() == CommonErrorCode.AI_CREDIT_INSUFFICIENT) {
                return Outcome.INSUFFICIENT;
            }
            throw e;

        } catch (PessimisticLockingFailureException
                 | PessimisticLockException
                 | LockTimeoutException e) {
            return Outcome.LOCK_TIMEOUT;
        }
    }

    private enum Outcome {
        SUCCESS, INSUFFICIENT, LOCK_TIMEOUT
    }
}
