package com.moviepick.domain.admin.controller;

import com.moviepick.domain.community.service.CommentService;
import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 관리자 댓글 숨김은 PATCH /api/admin/comments/{commentId}/blind, 기존 DELETE 주소는 없음 (게시글·리뷰 숨김과 같은 모양)
@WebMvcTest(
        controllers = AdminCommentController.class,
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
@WithMockUser(roles = "ADMIN")
class AdminCommentBlindTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

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
    @DisplayName("PATCH /blind → 200, 반복 호출도 200")
    void blind_patch_ok() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(patch("/api/admin/comments/5/blind").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        verify(commentService, times(2)).blindComment(5L);
    }

    @Test
    @DisplayName("기존 DELETE /api/admin/comments/{id} 주소는 없음")
    void oldDelete_removed() throws Exception {
        mockMvc.perform(delete("/api/admin/comments/5").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PATH_NOT_FOUND"));

        verifyNoInteractions(commentService);
    }
}
