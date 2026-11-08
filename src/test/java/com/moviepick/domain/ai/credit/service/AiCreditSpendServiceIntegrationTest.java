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
        AiCreditSpendService.class
})
class AiCreditSpendServiceIntegrationTest {

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

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {

                    Member member = memberRepository.saveAndFlush(
                            Member.createLocal(
                                    "ai-spend@test.com",
                                    "encoded-password",
                                    "AI차감테스트"
                            )
                    );

                    memberId = member.getId();

                    AiCreditWallet wallet = walletRepository.saveAndFlush(
                            new AiCreditWallet(member)
                    );

                    wallet.grantFreeCredits(3);

                    transactionRepository.saveAndFlush(
                            AiCreditTransaction.freeGrant(
                                    wallet,
                                    3,
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
    void 무료_크레딧을_먼저_1회_차감한다() {

        String requestKey = UUID.randomUUID().toString();

        var result = spendService.spendOne(memberId, requestKey);

        assertThat(result.bucket()).isEqualTo(AiCreditBucket.FREE);
        assertThat(result.freeAvailable()).isEqualTo(2);
        assertThat(result.paidAvailable()).isZero();
        assertThat(result.duplicate()).isFalse();

        var wallet = walletRepository
                .findByMember_Id(memberId)
                .orElseThrow();

        assertThat(wallet.getFreeAvailable()).isEqualTo(2);

        var transaction = transactionRepository.findByIdempotencyKey(
                "USE:MEMBER:" + memberId + ":REQ:" + requestKey
        ).orElseThrow();

        assertThat(transaction.getEventType())
                .isEqualTo(AiCreditEventType.USE);
        assertThat(transaction.getBucket())
                .isEqualTo(AiCreditBucket.FREE);
        assertThat(transaction.getDelta()).isEqualTo(-1);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_요청은_두번_차감하지_않는다() {

        String requestKey = UUID.randomUUID().toString();

        var first = spendService.spendOne(memberId, requestKey);
        var second = spendService.spendOne(memberId, requestKey);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();

        assertThat(second.freeAvailable()).isEqualTo(2);

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(2);

        // 최초 지급 1건 + 사용 1건
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 크레딧이_모두_소진되면_추가_사용할_수_없다() {

        for (int i = 0; i < 3; i++) {
            spendService.spendOne(
                    memberId,
                    UUID.randomUUID().toString()
            );
        }

        assertThatThrownBy(() -> spendService.spendOne(
                memberId,
                UUID.randomUUID().toString()
        )).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(AiErrorCode.AI_CREDIT_INSUFFICIENT)
        );

        var wallet = walletRepository
                .findByMember_Id(memberId)
                .orElseThrow();

        assertThat(wallet.getFreeAvailable()).isZero();
        assertThat(wallet.getPaidAvailable()).isZero();

        // 최초 지급 1건 + 사용 3건
        assertThat(transactionRepository.count()).isEqualTo(4);
    }

    @Test
    void 차감후_트랜잭션이_실패하면_모두_롤백된다() {

        String requestKey = UUID.randomUUID().toString();

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {

                            spendService.spendOne(memberId, requestKey);

                            throw new IllegalStateException("후속 처리 실패");
                        })
        ).isInstanceOf(IllegalStateException.class);

        var wallet = walletRepository
                .findByMember_Id(memberId)
                .orElseThrow();

        assertThat(wallet.getFreeAvailable()).isEqualTo(3);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 잘못된_요청키는_거부한다() {

        assertThatThrownBy(() ->
                spendService.spendOne(memberId, "invalid-key")
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT)
        );

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(3);

        assertThat(transactionRepository.count()).isEqualTo(1);
    }
}
