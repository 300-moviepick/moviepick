package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 지갑이 없는 회원(AI 기능 이전 가입자·시드 계정)의 지갑 자동 생성
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JpaAuditingConfig.class,
        AiCreditWalletProvisioner.class,
        AiSignupCreditService.class,
        AiCreditQueryService.class,
        AiCreditSpendService.class
})
class AiCreditWalletProvisionerIntegrationTest {

    @Autowired
    private AiCreditWalletProvisioner provisioner;

    @Autowired
    private AiCreditQueryService queryService;

    @Autowired
    private AiCreditSpendService spendService;

    @Autowired
    private AiCreditWalletRepository walletRepository;

    @Autowired
    private AiCreditTransactionRepository transactionRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        // 지갑 없이 회원만 저장 (가입 지급을 거치지 않은 기존 회원)
        Member member = memberRepository.saveAndFlush(
                Member.createLocal(
                        "no-wallet@test.com",
                        "encoded-password",
                        "지갑없는회원"
                )
        );
        memberId = member.getId();
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 조회_전에_지갑이_없으면_무료_3회_지갑을_만든다() {

        provisioner.ensureWallet(memberId);

        var balance = queryService.getBalance(memberId);

        assertThat(balance.freeAvailable()).isEqualTo(3);
        assertThat(balance.paidAvailable()).isZero();
        assertThat(freeGrantCount()).isEqualTo(1);
    }

    @Test
    void 사용_전에_지갑이_없으면_만들고_1회_차감한다() {

        provisioner.ensureWallet(memberId);

        var result = spendService.spendOne(
                memberId,
                UUID.randomUUID().toString()
        );

        assertThat(result.duplicate()).isFalse();
        assertThat(result.freeAvailable()).isEqualTo(2);
    }

    @Test
    void 이미_지갑이_있으면_다시_지급하지_않는다() {

        provisioner.ensureWallet(memberId);
        provisioner.ensureWallet(memberId);

        AiCreditWallet wallet =
                walletRepository.findByMember_Id(memberId).orElseThrow();

        assertThat(wallet.getFreeAvailable()).isEqualTo(3);
        assertThat(freeGrantCount()).isEqualTo(1);
    }

    @Test
    void 동시에_요청해도_지갑은_1개만_생긴다() throws Exception {

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                provisioner.ensureWallet(memberId);
                return null;
            }));
        }

        start.countDown();

        // 모든 스레드가 예외 없이 끝나야 한다 (충돌은 내부에서 정상 처리)
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        pool.shutdown();

        assertThat(walletRepository.findAll())
                .filteredOn(w -> w.getMember().getId().equals(memberId))
                .hasSize(1);

        assertThat(walletRepository.findByMember_Id(memberId).orElseThrow()
                .getFreeAvailable()).isEqualTo(3);

        assertThat(freeGrantCount()).isEqualTo(1);
    }

    private long freeGrantCount() {
        return transactionRepository.findAll().stream()
                .map(AiCreditTransaction::getEventType)
                .filter(type -> type == AiCreditEventType.FREE_GRANT)
                .count();
    }
}
