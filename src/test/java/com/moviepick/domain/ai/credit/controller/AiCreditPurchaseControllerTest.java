package com.moviepick.domain.ai.credit.controller;

import com.moviepick.domain.ai.credit.dto.request.AiCreditPurchaseRequest;
import com.moviepick.domain.ai.credit.dto.response.AiCreditBalanceResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditProductResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditPurchaseConfirmResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditPurchaseCreateResponse;
import com.moviepick.domain.ai.credit.entity.AiCreditProduct;
import com.moviepick.domain.ai.credit.service.AiCreditPurchaseService;
import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.support.dto.response.ConfirmResult;
import com.moviepick.domain.support.entity.PaymentStatus;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AI 크레딧 구매 API. 상품 목록·구매 생성은 AI가 켜졌을 때만, confirm은 항상 등록된다.
@WebMvcTest(
        controllers = {AiCreditStoreController.class, AiCreditPurchaseController.class},
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
        OAuth2LoginFailureHandler.class
})
@ActiveProfiles("test")
class AiCreditPurchaseControllerTest {

    static final Long MEMBER_ID = 5L;
    static final String PAYMENT_ID = "credit-550e8400-e29b-41d4-a716-446655440000";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AiCreditPurchaseService purchaseService;
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

    MockHttpServletRequestBuilder asMember(MockHttpServletRequestBuilder request) {
        var auth = new UsernamePasswordAuthenticationToken(
                MEMBER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return request.with(authentication(auth)).with(csrf());
    }

    MockHttpServletRequestBuilder purchase(String body) {
        return post("/api/ai/credits/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    void givenConfirmCompleted() {
        given(purchaseService.confirm(MEMBER_ID, PAYMENT_ID)).willReturn(
                AiCreditPurchaseConfirmResponse.of(
                        PAYMENT_ID, PaymentStatus.PAID, ConfirmResult.COMPLETED,
                        new AiCreditBalanceResponse(3, 10, 13)));
    }

    @Nested
    @DisplayName("AI 켜짐 (chat=openai)")
    @TestPropertySource(properties = "spring.ai.model.chat=openai")
    class AiEnabled {

        @Test
        void 상품_목록을_가격표대로_준다() throws Exception {
            given(purchaseService.getProducts()).willReturn(
                    Arrays.stream(AiCreditProduct.values()).map(AiCreditProductResponse::from).toList());

            mockMvc.perform(asMember(get("/api/ai/credits/products")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].productCode").value("CREDIT_10"))
                    .andExpect(jsonPath("$.data[0].credits").value(10))
                    .andExpect(jsonPath("$.data[0].amount").value(1000));
        }

        @Test
        void 구매_생성은_후원_생성과_같은_201() throws Exception {
            given(purchaseService.createPurchase(eq(MEMBER_ID), any(AiCreditPurchaseRequest.class)))
                    .willReturn(new AiCreditPurchaseCreateResponse(
                            PAYMENT_ID, 1_000L, "MoviePick AI 크레딧 10회", "store", "channel"));

            mockMvc.perform(asMember(purchase("{\"productCode\":\"CREDIT_10\"}")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                    .andExpect(jsonPath("$.data.amount").value(1000))
                    .andExpect(jsonPath("$.data.storeId").value("store"))
                    .andExpect(jsonPath("$.data.channelKey").value("channel"));
        }

        @Test
        void 알_수_없는_상품_코드는_400() throws Exception {
            given(purchaseService.createPurchase(eq(MEMBER_ID), any(AiCreditPurchaseRequest.class)))
                    .willThrow(new BusinessException(CommonErrorCode.AI_CREDIT_PRODUCT_NOT_FOUND));

            mockMvc.perform(asMember(purchase("{\"productCode\":\"CREDIT_99\"}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("AI_CREDIT_PRODUCT_NOT_FOUND"));
        }

        @Test
        void 상품_코드가_비어_있으면_400() throws Exception {
            mockMvc.perform(asMember(purchase("{\"productCode\":\"\"}")))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(purchaseService);
        }

        @Test
        void 비로그인_구매_생성은_401() throws Exception {
            mockMvc.perform(purchase("{\"productCode\":\"CREDIT_10\"}").with(csrf()))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(purchaseService);
        }

        @Test
        void confirm은_잔액을_함께_준다() throws Exception {
            givenConfirmCompleted();

            mockMvc.perform(asMember(post("/api/ai/credits/purchases/{id}/confirm", PAYMENT_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAID"))
                    .andExpect(jsonPath("$.data.result").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.credits.paidAvailable").value(10));
        }
    }

    @Nested
    @DisplayName("AI 꺼짐 (chat=none)")
    @TestPropertySource(properties = "spring.ai.model.chat=none")
    class AiDisabled {

        @Test
        void 상품_목록은_404_PATH_NOT_FOUND() throws Exception {
            mockMvc.perform(asMember(get("/api/ai/credits/products")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PATH_NOT_FOUND"));
        }

        @Test
        void 구매_생성은_404_PATH_NOT_FOUND() throws Exception {
            mockMvc.perform(asMember(purchase("{\"productCode\":\"CREDIT_10\"}")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PATH_NOT_FOUND"));

            verifyNoInteractions(purchaseService);
        }

        @Test
        void 이미_만든_결제의_confirm은_그대로_처리된다() throws Exception {
            givenConfirmCompleted();

            mockMvc.perform(asMember(post("/api/ai/credits/purchases/{id}/confirm", PAYMENT_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.result").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.credits.paidAvailable").value(10));
        }
    }
}
