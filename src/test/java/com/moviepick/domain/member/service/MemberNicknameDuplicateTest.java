package com.moviepick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import com.moviepick.domain.ai.credit.service.AiSignupCreditService;
import com.moviepick.domain.member.dto.request.MemberUpdateRequest;
import com.moviepick.domain.member.dto.request.SignupRequest;
import com.moviepick.domain.member.dto.response.MemberResponse;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 중복: 가입·수정 409 DUPLICATE_NICKNAME, 자기 닉네임 수정은 허용 (AUTH-15, B6) */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, PasswordConfig.class, AuthService.class, MemberService.class})
class MemberNicknameDuplicateTest {

    private static final String PASSWORD = "password123!";

    @Autowired
    private AuthService authService;
    @Autowired
    private MemberService memberService;
    @MockitoSpyBean
    private MemberRepository memberRepository;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private RefreshTokenService refreshTokenService;
    // [AI 추가] 기존 회원 도메인 테스트에서 무료 지급 호출 대체
    @MockitoBean
    private AiSignupCreditService aiSignupCreditService;

    @AfterEach
    void tearDown() {
        Mockito.reset(memberRepository);
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("가입: 다른 회원과 같은 닉네임이면 409 DUPLICATE_NICKNAME")
    void signupDuplicateNickname() {
        signup("first@test.com", "같은이름");

        assertDuplicateNickname(() -> signup("second@test.com", "같은이름"));
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("수정: 다른 회원의 닉네임으로 바꾸면 409 DUPLICATE_NICKNAME, 닉네임은 그대로")
    void updateDuplicateNickname() {
        signup("first@test.com", "먼저쓴이름");
        Long memberId = signup("second@test.com", "내이름").memberId();

        assertDuplicateNickname(() -> memberService.updateMember(memberId, new MemberUpdateRequest("먼저쓴이름")));
        assertThat(memberRepository.findById(memberId).orElseThrow().getNickname()).isEqualTo("내이름");
    }

    @Test
    @DisplayName("수정: 자기 닉네임과 같은 값으로 바꾸면 성공")
    void updateToOwnNickname() {
        Long memberId = signup("me@test.com", "내이름").memberId();

        MemberResponse response = memberService.updateMember(memberId, new MemberUpdateRequest("내이름"));

        assertThat(response.nickname()).isEqualTo("내이름");
    }

    @Test
    @DisplayName("탈퇴 회원의 원래 닉네임으로는 새로 가입할 수 있다")
    void signupWithWithdrawnMembersNickname() {
        Long withdrawnId = signup("leave@test.com", "떠난이름").memberId();
        memberService.deleteMember(withdrawnId);

        MemberResponse response = signup("new@test.com", "떠난이름");

        assertThat(response.nickname()).isEqualTo("떠난이름");
    }

    @Test
    @DisplayName("가입 동시 요청: 사전 확인을 통과해도 유니크 제약으로 409 DUPLICATE_NICKNAME, 회원은 1명")
    void concurrentSignupDuplicateNickname() {
        signup("first@test.com", "같은이름");
        // 동시 요청: 사전 확인에서 아직 "없음"을 본 상황
        doReturn(false).when(memberRepository).existsByNickname(anyString());

        assertDuplicateNickname(() -> signup("second@test.com", "같은이름"));
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("수정 동시 요청: 사전 확인을 통과해도 유니크 제약으로 409 DUPLICATE_NICKNAME, 닉네임은 그대로")
    void concurrentUpdateDuplicateNickname() {
        signup("first@test.com", "먼저쓴이름");
        Long memberId = signup("second@test.com", "내이름").memberId();
        doReturn(false).when(memberRepository).existsByNicknameAndIdNot(anyString(), anyLong());

        assertDuplicateNickname(() -> memberService.updateMember(memberId, new MemberUpdateRequest("먼저쓴이름")));
        assertThat(memberRepository.findById(memberId).orElseThrow().getNickname()).isEqualTo("내이름");
    }

    private MemberResponse signup(String email, String nickname) {
        return authService.signup(new SignupRequest(email, PASSWORD, nickname));
    }

    private void assertDuplicateNickname(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().toString())
                .isEqualTo("DUPLICATE_NICKNAME");
    }
}
