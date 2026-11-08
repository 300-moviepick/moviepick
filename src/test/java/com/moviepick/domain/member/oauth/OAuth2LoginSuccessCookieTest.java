package com.moviepick.domain.member.oauth;

import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.FrontendProperties;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenCookieFactory;
import com.moviepick.global.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// 소셜 로그인 성공 시 refreshToken 쿠키의 Secure 속성이 app.cookie.secure 값을 따르는지
class OAuth2LoginSuccessCookieTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = handlerWithSecureCookie(false);

        Member member = mock(Member.class);
        given(member.getId()).willReturn(7L);
        given(member.getStatus()).willReturn(MemberStatus.ACTIVE);

        given(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "123"))
                .willReturn(Optional.of(member));
        given(refreshTokenService.getAuthVersion(7L)).willReturn(0L);
        given(jwtProvider.createRefreshToken(7L, 0L)).willReturn("refresh-token");
    }

    private OAuth2LoginSuccessHandler handlerWithSecureCookie(boolean secure) {
        return new OAuth2LoginSuccessHandler(
                memberRepository,
                jwtProvider,
                refreshTokenService,
                new FrontendProperties("http://localhost:5173", List.of()),
                new RefreshTokenCookieFactory(secure)
        );
    }

    private String successCookie() throws Exception {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var user = new DefaultOAuth2User(authorities, Map.of("id", 123L), "id");
        var token = new OAuth2AuthenticationToken(user, authorities, "kakao");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, token);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/oauth2/success");

        return response.getHeader(HttpHeaders.SET_COOKIE);
    }

    @Test
    void 기본값이면_Secure가_없다() throws Exception {
        String cookie = successCookie();

        assertThat(cookie).startsWith("refreshToken=refresh-token");
        assertThat(cookie).doesNotContain("Secure");
        assertThat(cookie).contains("SameSite=Strict", "HttpOnly", "Path=/api/auth", "Max-Age=1209600");
    }

    @Test
    void 설정값이_true면_Secure가_붙는다() throws Exception {
        handler = handlerWithSecureCookie(true);

        assertThat(successCookie())
                .contains("Secure", "SameSite=Strict", "HttpOnly");
    }
}
