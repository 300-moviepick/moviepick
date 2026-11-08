package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.exception.AiErrorCode;
import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JpaAuditingConfig.class,
        AiCreditSpendService.class,
        AiCreditRestoreService.class
})
class AiCreditRestoreServiceIntegrationTest {

    @Autowired
    private AiCreditRestoreService restoreService;

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
    private String requestKey;

    @BeforeEach
    void setUp() {

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {

                    Member member = memberRepository.saveAndFlush(
                            Member.createLocal(
                                    "restore@test.com",
                                    "encoded-password",
                                    "AI복구테스트"
                            )
                    );

                    memberId = member.getId();

                    AiCreditWallet wallet = new AiCreditWallet(member);
                    wallet.grantFreeCredits(3);
                    walletRepository.saveAndFlush(wallet);

                    transactionRepository.saveAndFlush(
                            AiCreditTransaction.freeGrant(
                                    wallet,
                                    3,
                                    "FREE_GRANT:MEMBER:" + memberId
                            )
                    );
                });

        // 모든 테스트는 1회 차감된 상태에서 시작
        requestKey = UUID.randomUUID().toString();
        spendService.spendOne(memberId, requestKey);
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 사용한_무료_크레딧을_복구한다() {

        var result = restoreService.restoreOne(memberId, requestKey);

        assertThat(result.bucket()).isEqualTo(AiCreditBucket.FREE);
        assertThat(result.freeAvailable()).isEqualTo(3);
        assertThat(result.paidAvailable()).isZero();
        assertThat(result.duplicate()).isFalse();

        var wallet = walletRepository
                .findByMember_Id(memberId)
                .orElseThrow();

        assertThat(wallet.getFreeAvailable()).isEqualTo(3);

        String restoreKey = "RESTORE:MEMBER:" + memberId
                + ":REQ:" + requestKey;

        var transaction = transactionRepository
                .findByIdempotencyKey(restoreKey)
                .orElseThrow();

        assertThat(transaction.getEventType())
                .isEqualTo(AiCreditEventType.RESTORE);
        assertThat(transaction.getBucket())
                .isEqualTo(AiCreditBucket.FREE);
        assertThat(transaction.getDelta()).isEqualTo(1);
        assertThat(transaction.getRequestKey()).isEqualTo(requestKey);

        // 지급 + 사용 + 복구 = 3건
        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    void 동일한_요청은_두번_복구하지_않는다() {

        var first = restoreService.restoreOne(memberId, requestKey);
        var second = restoreService.restoreOne(memberId, requestKey);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(3);

        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    void 사용_기록이_없으면_복구할_수_없다() {

        String unknownKey = UUID.randomUUID().toString();

        assertThatThrownBy(() ->
                restoreService.restoreOne(memberId, unknownKey)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(AiErrorCode.AI_CREDIT_USE_NOT_FOUND)
        );

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(2);

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 복구후_트랜잭션이_실패하면_롤백된다() {

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {

                            restoreService.restoreOne(memberId, requestKey);

                            throw new IllegalStateException("복구 후 오류");
                        })
        ).isInstanceOf(IllegalStateException.class);

        // 복구 전 상태 유지
        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(2);

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 잘못된_요청키로_복구할_수_없다() {

        assertThatThrownBy(() ->
                restoreService.restoreOne(memberId, "invalid-uuid")
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT)
        );

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(2);

        assertThat(transactionRepository.count()).isEqualTo(2);
    }
}