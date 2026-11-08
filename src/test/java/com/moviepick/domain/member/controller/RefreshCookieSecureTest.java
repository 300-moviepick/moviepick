package com.moviepick.domain.member.controller;

import com.moviepick.global.security.RefreshTokenCookieFactory;
import com.moviepick.domain.member.dto.response.LoginResponse;
import com.moviepick.domain.member.dto.response.LoginResult;
import com.moviepick.domain.member.entity.Role;
import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.domain.member.service.AuthService;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// refreshToken 쿠키의 Secure 속성이 app.cookie.secure 값을 따르는지 (SameSite=Strict는 유지)
@WebMvcTest(
        controllers = AuthController.class,
        properties = {
                "spring.security.oauth2.client.registration.google.client-id=test-google",
                "spring.security.oauth2.client.registration.google.client-secret=test-secret",
                "spring.security.oauth2.client.registration.kakao.client-id=test-kakao",
                "spring.security.oauth2.client.registration.kakao.client-secret=test-secret"
        }
)
@Import({
        RefreshTokenCookieFactory.class,
        SecurityConfig.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class,
        MemberStatusChecker.class,
        OAuth2LoginFailureHandler.class
})
@ActiveProfiles("test")
class RefreshCookieSecureTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;
    @MockitoBean
    MemberService memberService;
    @MockitoBean
    JwtProvider jwtProvider;
    @MockitoBean
    CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    CustomOidcUserService customOidcUserService;
    @MockitoBean
    OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    String loginCookie() throws Exception {
        given(authService.login(any())).willReturn(new LoginResult(
                new LoginResponse(1L, "쿠키", Role.USER, "access", "Bearer", 1800),
                "refresh-token"
        ));

        return mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie@test.local","password":"password1"}
                                """))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    String logoutCookie() throws Exception {
        return mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    @Nested
    @DisplayName("기본값(app.cookie.secure 미지정)")
    class DefaultValue {

        @Test
        void 로그인_쿠키에_Secure가_없다() throws Exception {
            String cookie = loginCookie();

            assertThat(cookie).startsWith("refreshToken=refresh-token");
            assertThat(cookie).doesNotContain("Secure");
            assertThat(cookie).contains("SameSite=Strict", "HttpOnly", "Path=/api/auth", "Max-Age=1209600");
        }

        @Test
        void 로그아웃_쿠키에_Secure가_없다() throws Exception {
            assertThat(logoutCookie())
                    .doesNotContain("Secure")
                    .contains("Max-Age=0", "SameSite=Strict", "HttpOnly", "Path=/api/auth");
        }
    }

    @Nested
    @DisplayName("app.cookie.secure=true")
    @TestPropertySource(properties = "app.cookie.secure=true")
    class SecureTrue {

        @Test
        void 로그인_쿠키에_Secure가_붙는다() throws Exception {
            assertThat(loginCookie())
                    .contains("Secure", "SameSite=Strict", "HttpOnly");
        }

        @Test
        void 로그아웃_쿠키에도_Secure가_붙는다() throws Exception {
            assertThat(logoutCookie())
                    .contains("Secure", "Max-Age=0", "SameSite=Strict");
        }
    }
}
