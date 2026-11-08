package com.moviepick.domain.member.oauth;

import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.FrontendProperties;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenCookieFactory;
import com.moviepick.global.security.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// AUTH-05: 소셜 로그인 실패는 프론트 실패 화면(/oauth2/fail?reason=코드)으로 이동한다
class OAuth2LoginFailureHandlerTest {

    private static final String FAIL_URL = "http://localhost:5173/oauth2/fail?reason=";

    private final FrontendProperties frontendProperties = new FrontendProperties(null, null);

    private final OAuth2LoginFailureHandler failureHandler = new OAuth2LoginFailureHandler(frontendProperties);

    private String failRedirect(AuthenticationException exception) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);
        return response.getRedirectedUrl();
    }

    @Test
    @DisplayName("정지 회원 → reason=MEMBER_SUSPENDED")
    void suspended() throws Exception {
        assertThat(failRedirect(OAuth2LoginFailureHandler.inactiveMemberException(MemberStatus.SUSPENDED)))
                .isEqualTo(FAIL_URL + "MEMBER_SUSPENDED");
    }

    @Test
    @DisplayName("탈퇴 회원 → reason=MEMBER_WITHDRAWN")
    void withdrawn() throws Exception {
        assertThat(failRedirect(OAuth2LoginFailureHandler.inactiveMemberException(MemberStatus.WITHDRAWN)))
                .isEqualTo(FAIL_URL + "MEMBER_WITHDRAWN");
    }

    @Test
    @DisplayName("그 밖의 실패 → reason=OAUTH2_FAILED (예외 문구는 주소에 넣지 않음)")
    void otherFailure() throws Exception {
        assertThat(failRedirect(new OAuth2AuthenticationException(
                new OAuth2Error("authorization_request_not_found"), "세부 문구")))
                .isEqualTo(FAIL_URL + "OAUTH2_FAILED");
        assertThat(failRedirect(new BadCredentialsException("bad")))
                .isEqualTo(FAIL_URL + "OAUTH2_FAILED");
    }

    @Test
    @DisplayName("성공 처리기에서 정지 회원으로 확인되면 실패 화면으로 이동")
    void successHandler_suspendedMember_redirectsToFail() throws Exception {
        MemberRepository memberRepository = mock(MemberRepository.class);
        OAuth2LoginSuccessHandler successHandler = new OAuth2LoginSuccessHandler(
                memberRepository, mock(JwtProvider.class), mock(RefreshTokenService.class), frontendProperties,
                new RefreshTokenCookieFactory(false));

        Member member = Member.createSocial(AuthProvider.KAKAO, "123", null, "카카오회원");
        member.suspend(null);
        given(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "123"))
                .willReturn(Optional.of(member));

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                new DefaultOAuth2User(authorities, Map.of("id", 123L), "id"), authorities, "kakao");

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(FAIL_URL + "MEMBER_SUSPENDED");
    }
}
