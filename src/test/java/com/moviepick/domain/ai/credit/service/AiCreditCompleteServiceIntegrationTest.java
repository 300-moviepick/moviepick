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
        AiCreditRestoreService.class,
        AiCreditCompleteService.class
})
class AiCreditCompleteServiceIntegrationTest {

    @Autowired
    private AiCreditCompleteService completeService;

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
                                    "complete@test.com",
                                    "encoded-password",
                                    "AI완료테스트"
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

        requestKey = UUID.randomUUID().toString();

        // 최초 1회 차감된 상태에서 테스트 시작
        spendService.spendOne(memberId, requestKey);
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 사용한_크레딧을_성공_확정한다() {

        var result = completeService.completeOne(memberId, requestKey);

        assertThat(result.duplicate()).isFalse();

        String completeKey = "COMPLETE:MEMBER:" + memberId
                + ":REQ:" + requestKey;

        var transaction = transactionRepository
                .findByIdempotencyKey(completeKey)
                .orElseThrow();

        assertThat(transaction.getEventType())
                .isEqualTo(AiCreditEventType.COMPLETE);
        assertThat(transaction.getDelta()).isZero();

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(2);

        // FREE_GRANT + USE + COMPLETE
        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    void 동일_요청을_두번_완료해도_한번만_기록된다() {

        var first = completeService.completeOne(memberId, requestKey);
        var second = completeService.completeOne(memberId, requestKey);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();

        assertThat(transactionRepository.count()).isEqualTo(3);

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(2);
    }

    @Test
    void 완료된_요청은_복구할_수_없다() {

        completeService.completeOne(memberId, requestKey);

        assertThatThrownBy(() ->
                restoreService.restoreOne(memberId, requestKey)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(
                                CommonErrorCode.AI_CREDIT_REQUEST_ALREADY_FINALIZED
                        )
        );

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(2);

        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    void 복구된_요청은_완료할_수_없다() {

        restoreService.restoreOne(memberId, requestKey);

        assertThatThrownBy(() ->
                completeService.completeOne(memberId, requestKey)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(
                                CommonErrorCode.AI_CREDIT_REQUEST_ALREADY_FINALIZED
                        )
        );

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(3);

        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    void 사용_이력이_없으면_완료할_수_없다() {

        String unknownKey = UUID.randomUUID().toString();

        assertThatThrownBy(() ->
                completeService.completeOne(memberId, unknownKey)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.AI_CREDIT_USE_NOT_FOUND)
        );

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 완료_트랜잭션에_오류가_발생하면_롤백된다() {

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {

                            completeService.completeOne(memberId, requestKey);

                            throw new IllegalStateException("완료 후 오류");
                        })
        ).isInstanceOf(IllegalStateException.class);

        String completeKey = "COMPLETE:MEMBER:" + memberId
                + ":REQ:" + requestKey;

        assertThat(transactionRepository
                .existsByIdempotencyKey(completeKey)).isFalse();

        assertThat(transactionRepository.count()).isEqualTo(2);

        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(2);
    }

    @Test
    void 완료된_요청키로_다시_차감할_수_없다() {

        completeService.completeOne(memberId, requestKey);

        assertThatThrownBy(() ->
                spendService.spendOne(memberId, requestKey)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(
                                CommonErrorCode.AI_CREDIT_REQUEST_ALREADY_FINALIZED
                        )
        );

        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    void 복구된_요청키로_다시_차감할_수_없다() {

        restoreService.restoreOne(memberId, requestKey);

        assertThatThrownBy(() ->
                spendService.spendOne(memberId, requestKey)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(
                                CommonErrorCode.AI_CREDIT_REQUEST_ALREADY_FINALIZED
                        )
        );

        assertThat(transactionRepository.count()).isEqualTo(3);
        assertThat(walletRepository
                .findByMember_Id(memberId)
                .orElseThrow()
                .getFreeAvailable()).isEqualTo(3);
    }
}