package com.moviepick.domain.member.oauth;

import com.moviepick.global.security.RefreshTokenCookieFactory;
import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.global.security.SecurityConfig;
import com.moviepick.domain.member.controller.AuthController;
import com.moviepick.domain.member.service.AuthService;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.global.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AUTH-05: 소셜 로그인 콜백이 실패하면 백엔드 /login?error가 아니라 프론트 실패 화면으로 이동한다
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
class OAuth2LoginFailureRedirectTest {

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

    @Test
    @DisplayName("인가 요청 없이 콜백 → 프론트 /oauth2/fail?reason=OAUTH2_FAILED")
    void callbackWithoutAuthorizationRequest_redirectsToFront() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/kakao")
                        .param("code", "dummy")
                        .param("state", "dummy"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/oauth2/fail?reason=OAUTH2_FAILED"));
    }
}
