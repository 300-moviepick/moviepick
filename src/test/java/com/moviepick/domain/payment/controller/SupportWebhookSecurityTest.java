package com.moviepick.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.payment.client.PortOneClient;
import com.moviepick.domain.payment.client.PortOneConfig;
import com.moviepick.domain.payment.client.PortOneProperties;
import com.moviepick.domain.payment.client.webhook.PortOneWebhookReceiver;
import com.moviepick.domain.payment.client.webhook.PortOneWebhookTestSigner;
import com.moviepick.domain.payment.client.webhook.PortOneWebhookVerifier;
import com.moviepick.domain.payment.service.SupportWebhookResult;
import com.moviepick.domain.payment.service.SupportWebhookService;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.SecurityConfig;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 실제 SecurityConfig로 웹훅 경로의 인증·CSRF 예외가 정확히 이 경로 하나에만 적용되는지 확인한다. */
@WebMvcTest(
        controllers = SupportWebhookController.class,
        properties = {
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
        OAuth2LoginFailureHandler.class,
        PortOneConfig.class,
        PortOneWebhookReceiver.class,
        PortOneWebhookVerifier.class
})
@ActiveProfiles("test")
class SupportWebhookSecurityTest {

    private static final String WEBHOOK_URL = "/api/webhooks/portone";
    private static final String BODY = PortOneWebhookTestSigner.paidBody("support-security-test");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortOneProperties portOneProperties;   // 테스트 설정(config/application.yml)의 더미 시크릿

    @MockitoBean
    private SupportWebhookService supportWebhookService;
    @MockitoBean
    private PortOneClient portOneClient;

    // JWT 필터 생성에 필요한 의존성
    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private JwtProvider jwtProvider;

    // SecurityConfig의 OAuth2 관련 의존성
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private CustomOidcUserService customOidcUserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @Test
    @DisplayName("로그인·CSRF 토큰 없이 올바른 서명이면 200")
    void noAuthNoCsrf() throws Exception {
        given(supportWebhookService.handle(any())).willReturn(SupportWebhookResult.PAID_APPLIED);

        mockMvc.perform(signed(post(WEBHOOK_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("PAID_APPLIED"));
    }

    @Test
    @DisplayName("잘못된 서명은 401 INVALID_SIGNATURE, 처리 서비스는 호출되지 않는다")
    void invalidSignature() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "msg_bad")
                        .header("webhook-timestamp", timestamp)
                        .header("webhook-signature", "v1,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SIGNATURE"));

        verifyNoInteractions(supportWebhookService);
    }

    @Test
    @DisplayName("서명 헤더가 없으면 401")
    void missingHeaders() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SIGNATURE"));

        verifyNoInteractions(supportWebhookService);
    }

    @Test
    @DisplayName("잘못된 Bearer 토큰이 붙어 와도 공개 주소라 서명만으로 처리된다")
    void invalidBearerIgnored() throws Exception {
        given(supportWebhookService.handle(any())).willReturn(SupportWebhookResult.IGNORED);
        // 실제 JwtProvider처럼 검증 실패 시 IllegalArgumentException
        given(jwtProvider.validateToken("invalid")).willThrow(new IllegalArgumentException("invalid token"));

        mockMvc.perform(signed(post(WEBHOOK_URL)).header("Authorization", "Bearer invalid"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개·CSRF 예외는 결제 웹훅 경로 하나뿐(예전 /support 포함 다른 경로는 보호): 다른 웹훅 경로는 CSRF 토큰이 없으면 403")
    void otherWebhookPathsStillCsrfProtected() throws Exception {
        mockMvc.perform(signed(post("/api/webhooks/portone/support")))
                .andExpect(status().isForbidden());
        mockMvc.perform(signed(post("/api/webhooks/portone/credit")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공개·CSRF 예외는 결제 웹훅 경로 하나뿐(예전 /support 포함 다른 경로는 보호): 다른 웹훅 경로는 CSRF 토큰이 있어도 인증 없으면 401")
    void otherWebhookPathsStillRequireAuth() throws Exception {
        mockMvc.perform(signed(post("/api/webhooks/portone/support")).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(signed(post("/api/webhooks/portone/credit")).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request) {
        String webhookId = "msg_security";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = PortOneWebhookTestSigner.sign(
                portOneProperties.webhookSecret(), webhookId, timestamp, BODY);
        return request.contentType(MediaType.APPLICATION_JSON)
                .header("webhook-id", webhookId)
                .header("webhook-timestamp", timestamp)
                .header("webhook-signature", signature)
                .content(BODY);
    }
}
