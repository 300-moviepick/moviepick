package com.moviepick.domain.member.controller;

import com.moviepick.global.security.RefreshTokenCookieFactory;
import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.global.security.SecurityConfig;
import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.domain.member.service.AuthService;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.global.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AUTH-13: 가입 비밀번호가 UTF-8로 72바이트를 넘으면 400 INVALID_INPUT
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
class SignupPasswordBytesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private CustomOidcUserService customOidcUserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    private ResultActions signup(String password) throws Exception {
        String body = """
                {"email": "bytes@test.local", "password": "%s", "nickname": "바이트"}
                """.formatted(password);

        return mockMvc.perform(post("/api/auth/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("한글 25자(75바이트) 비밀번호 → 400 INVALID_INPUT, 바이트 기준 메시지")
    void over72Bytes_badRequest() throws Exception {
        signup("가".repeat(25))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(containsString("바이트")));
    }

    @Test
    @DisplayName("한글 24자(72바이트) 비밀번호 → 가입 진행")
    void exactly72Bytes_created() throws Exception {
        signup("가".repeat(24))
                .andExpect(status().isCreated());
    }
}
