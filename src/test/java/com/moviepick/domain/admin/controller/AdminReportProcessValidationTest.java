package com.moviepick.domain.admin.controller;

import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.report.repository.AdminSanctionRepository;
import com.moviepick.domain.report.service.AdminReportService;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.community.service.CommentService;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenService;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.global.security.SecurityConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// R21: 신고 처리 요청의 status가 없거나 처리 결과가 아닌 값이면 DB 조회 전에 400 INVALID_INPUT
@WebMvcTest(
        controllers = {AdminReportController.class, AdminMemberController.class},
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
        AdminReportService.class,
        MemberService.class
})
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class AdminReportProcessValidationTest {

    @Autowired
    private MockMvc mockMvc;

    // 실제 서비스를 쓰고 저장소만 모킹한다
    @MockitoBean
    private CommunityReportRepository communityReportRepository;
    @MockitoBean
    private AdminSanctionRepository adminSanctionRepository;
    @MockitoBean
    private MemberRepository memberRepository;
    @MockitoBean
    private PostRepository postRepository;
    @MockitoBean
    private CommentRepository commentRepository;
    @MockitoBean
    private ReviewRepository reviewRepository;
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private CommentService commentService;
    @MockitoBean
    private EntityManager entityManager;

    @MockitoBean
    private RefreshTokenService refreshTokenService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private CustomOidcUserService customOidcUserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @Test
    @DisplayName("status가 없거나 null이면 400 INVALID_INPUT, 신고를 조회하지 않는다")
    void missingStatus() throws Exception {
        for (String body : new String[]{"{}", "{\"status\":null}"}) {
            requestProcess(body);
        }
        verifyNoInteractions(communityReportRepository);
    }

    @Test
    @DisplayName("처리 결과가 아닌 PENDING이면 400 INVALID_INPUT")
    void pendingStatus() throws Exception {
        requestProcess("{\"status\":\"PENDING\"}");
        verifyNoInteractions(communityReportRepository);
    }

    private void requestProcess(String body) throws Exception {
        mockMvc.perform(patch("/api/admin/reports/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }
}
