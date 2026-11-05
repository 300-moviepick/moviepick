package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.domain.member.dto.request.SignupRequest;
import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.AuthService;
import com.moviepick.domain.member.service.SocialMemberRegistrar;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.config.PasswordConfig;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JpaAuditingConfig.class,
        PasswordConfig.class,
        AiSignupCreditService.class,
        AuthService.class,
        SocialMemberRegistrar.class
})
class AiSignupCreditIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private SocialMemberRegistrar socialMemberRegistrar;

    @Autowired
    private AiSignupCreditService signupCreditService;

    @Autowired
    private AiCreditWalletRepository walletRepository;

    @Autowired
    private AiCreditTransactionRepository transactionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 일반_회원가입시_무료_3회와_원장_1건() {

        Long memberId = authService.signup(
                new SignupRequest(
                        "ai-local@test.com",
                        "password123!",
                        "AI일반회원"
                )
        ).memberId();

        var wallet = walletRepository.findByMember_Id(memberId)
                .orElseThrow();

        var transaction = transactionRepository.findByIdempotencyKey(
                "FREE_GRANT:MEMBER:" + memberId
        ).orElseThrow();

        assertThat(wallet.getFreeAvailable()).isEqualTo(3);
        assertThat(wallet.getPaidAvailable()).isZero();

        assertThat(transaction.getWallet().getId())
                .isEqualTo(wallet.getId());

        assertThat(transaction.getEventType())
                .isEqualTo(AiCreditEventType.FREE_GRANT);

        assertThat(transaction.getBucket())
                .isEqualTo(AiCreditBucket.FREE);

        assertThat(transaction.getDelta()).isEqualTo(3);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 동일_회원에게_중복_지급하지_않는다() {

        Long memberId = authService.signup(
                new SignupRequest(
                        "ai-repeat@test.com",
                        "password123!",
                        "AI중복검증"
                )
        ).memberId();

        Member member = memberRepository.findById(memberId)
                .orElseThrow();

        // 같은 회원에게 두 번째 지급 시도
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        signupCreditService.grantTo(member)
                );

        var wallet = walletRepository.findByMember_Id(memberId)
                .orElseThrow();

        assertThat(wallet.getFreeAvailable()).isEqualTo(3);
        assertThat(walletRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 소셜_최초가입시_지급하고_재로그인시_중복_지급하지_않는다() {

        Long firstId = socialMemberRegistrar.findOrRegister(
                AuthProvider.KAKAO,
                "ai-social-123",
                "ai-social@test.com",
                "AI소셜회원"
        );

        Long secondId = socialMemberRegistrar.findOrRegister(
                AuthProvider.KAKAO,
                "ai-social-123",
                "ai-social@test.com",
                "AI소셜회원"
        );

        assertThat(secondId).isEqualTo(firstId);

        assertThat(
                walletRepository.findByMember_Id(firstId)
                        .orElseThrow()
                        .getFreeAvailable()
        ).isEqualTo(3);

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 지급_도중_예외가_나면_모두_롤백한다() {

        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                template.executeWithoutResult(status -> {

                    Member member = memberRepository.saveAndFlush(
                            Member.createLocal(
                                    "ai-rollback@test.com",
                                    "encoded",
                                    "AI롤백회원"
                            )
                    );

                    signupCreditService.grantTo(member);

                    // 지급 이후 오류 발생 상황
                    throw new IllegalStateException("가입 실패 가정");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(memberRepository.count()).isZero();
        assertThat(walletRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }
}