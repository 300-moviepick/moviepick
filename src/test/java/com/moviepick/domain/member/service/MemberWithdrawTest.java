package com.moviepick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.ai.credit.service.AiSignupCreditService;
import com.moviepick.domain.member.dto.request.LoginRequest;
import com.moviepick.domain.member.dto.request.SignupRequest;
import com.moviepick.domain.member.dto.response.MemberResponse;
import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenService;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.config.PasswordConfig;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴 시 이메일·닉네임·소셜 키를 바꿔 재가입을 막지 않는지 (AUTH-09·AUTH-16, B5·B6) */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, PasswordConfig.class, AuthService.class, MemberService.class,
        SocialMemberRegistrar.class})
class MemberWithdrawTest {

    private static final String PASSWORD = "password123!";

    @Autowired
    private AuthService authService;
    @Autowired
    private MemberService memberService;
    @Autowired
    private SocialMemberRegistrar socialMemberRegistrar;
    @Autowired
    private MemberRepository memberRepository;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private RefreshTokenService refreshTokenService;
    // 회원가입 혜택(AI 크레딧 지급)은 이 테스트의 범위 밖이라 mock으로 대체
    @MockitoBean
    private AiSignupCreditService aiSignupCreditService;

    @AfterEach
    void tearDown() {
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("LOCAL 회원 탈퇴: 이메일·닉네임이 withdrawn_{회원번호} 형식으로 바뀌고 상태·시각이 기록된다")
    void withdrawLocalMember() {
        Long memberId = signup("leave@test.com", "떠날회원").memberId();

        memberService.deleteMember(memberId);

        Member withdrawn = memberRepository.findById(memberId).orElseThrow();
        assertThat(withdrawn.getEmail()).isEqualTo("withdrawn_" + memberId + "@withdrawn.local");
        assertThat(withdrawn.getNickname()).isEqualTo("withdrawn_" + memberId);
        assertThat(withdrawn.getProviderId()).isNull();
        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
    }

    @Test
    @DisplayName("소셜 회원 탈퇴: provider_id도 withdrawn_{회원번호}로 바뀐다")
    void withdrawSocialMember() {
        Long memberId = memberRepository.save(
                Member.createSocial(AuthProvider.KAKAO, "2001", "social@test.com", "소셜회원")).getId();

        memberService.deleteMember(memberId);

        Member withdrawn = memberRepository.findById(memberId).orElseThrow();
        assertThat(withdrawn.getProviderId()).isEqualTo("withdrawn_" + memberId);
        assertThat(withdrawn.getEmail()).isEqualTo("withdrawn_" + memberId + "@withdrawn.local");
        assertThat(withdrawn.getNickname()).isEqualTo("withdrawn_" + memberId);
    }

    @Test
    @DisplayName("탈퇴한 이메일로 다시 가입하면 완전히 새 회원이 된다")
    void rejoinWithSameEmail() {
        Long oldId = signup("again@test.com", "다시온회원").memberId();
        memberService.deleteMember(oldId);

        MemberResponse rejoined = signup("again@test.com", "새로온회원");

        assertThat(rejoined.memberId()).isNotEqualTo(oldId);
        assertThat(memberRepository.findById(rejoined.memberId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("탈퇴 회원이 원래 이메일로 로그인하면 없는 이메일과 같은 401 INVALID_CREDENTIALS")
    void loginWithWithdrawnEmail() {
        Long memberId = signup("gone@test.com", "사라진회원").memberId();
        memberService.deleteMember(memberId);

        assertThatThrownBy(() -> authService.login(new LoginRequest("gone@test.com", PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("탈퇴한 소셜 계정으로 다시 로그인하면 새 회원으로 가입된다")
    void rejoinWithSameSocialAccount() {
        Long oldId = socialMemberRegistrar.findOrRegister(AuthProvider.GOOGLE, "g-2001", null, "구글회원");
        memberService.deleteMember(oldId);

        Long newId = socialMemberRegistrar.findOrRegister(AuthProvider.GOOGLE, "g-2001", null, "구글회원");

        assertThat(newId).isNotEqualTo(oldId);
        assertThat(memberRepository.findById(newId).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    private MemberResponse signup(String email, String nickname) {
        return authService.signup(new SignupRequest(email, PASSWORD, nickname));
    }
}
