package com.moviepick.domain.ai.recommendation.controller;

import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AI_CHAT_PROVIDER=none(기본값)이면 추천 컨트롤러가 등록되지 않는다
@WebMvcTest(
        controllers = AiRecommendationController.class,
        properties = {
                "spring.ai.model.chat=none",
                "spring.security.oauth2.client.registration.google.client-id=test-google",
                "spring.security.oauth2.client.registration.google.client-secret=test-secret",
                "spring.security.oauth2.client.registration.kakao.client-id=test-kakao",
                "spring.security.oauth2.client.registration.kakao.client-secret=test-secret"
        }
)
@Import({
        SecurityConfig.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class,
        MemberStatusChecker.class,
        OAuth2LoginFailureHandler.class
})
@ActiveProfiles("test")
class AiRecommendationDisabledTest {

    private static final String BODY = """
            {"prompt":"감동적인 영화 추천해 줘","requestKey":"550e8400-e29b-41d4-a716-446655440000"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

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
    @DisplayName("chat=none이면 추천 컨트롤러 빈이 없다")
    void controller_not_registered() {
        assertThat(context.getBeanNamesForType(AiRecommendationController.class))
                .isEmpty();
    }

    @Test
    @WithMockUser
    @DisplayName("로그인 사용자의 추천 요청은 404 PATH_NOT_FOUND")
    void loggedIn_notFound() throws Exception {
        mockMvc.perform(post("/api/ai/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PATH_NOT_FOUND"));
    }

    @Test
    @DisplayName("비로그인 추천 요청은 401")
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(post("/api/ai/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
