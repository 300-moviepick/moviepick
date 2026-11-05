package com.moviepick.domain.admin.controller;

import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.report.repository.AdminSanctionRepository;
import com.moviepick.domain.report.service.AdminReportService;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.community.service.CommentService;
import com.moviepick.domain.report.entity.ReportStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.mockito.ArgumentCaptor;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ADMIN-05: 관리자 신고·회원 목록이 size를 받고, 기본값·범위는 공통 페이지 규칙(기본 20, 1~100)을 따른다
// 신고 목록은 status 필터(선택)와 최신순 정렬을 쓴다
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
class AdminListSizeTest {

    @Autowired
    private MockMvc mockMvc;

    // 실제 서비스를 쓰고 저장소만 모킹해, 서비스에 전달된 페이지 크기를 응답 size로 확인한다
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

    @BeforeEach
    void setUp() {
        given(communityReportRepository.findAll(any(Pageable.class)))
                .willAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(0, Pageable.class), 0));
        given(communityReportRepository.findByStatus(any(ReportStatus.class), any(Pageable.class)))
                .willAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1, Pageable.class), 0));
        given(memberRepository.findAll(any(Pageable.class)))
                .willAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(0, Pageable.class), 0));
    }

    @Test
    @DisplayName("신고 목록 size=50 → 50, 생략 → 20")
    void reports_size() throws Exception {
        mockMvc.perform(get("/api/admin/reports").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("회원 목록 size=50 → 50, 생략 → 20")
    void members_size() throws Exception {
        mockMvc.perform(get("/api/admin/members").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("size=101 → 400 INVALID_PAGE_REQUEST")
    void sizeOutOfRange() throws Exception {
        mockMvc.perform(get("/api/admin/reports").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_REQUEST"));
        mockMvc.perform(get("/api/admin/members").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_REQUEST"));
    }

    @Test
    @DisplayName("신고 목록 정렬은 createdAt 내림차순 → id 내림차순, status 생략 시 전체(findAll)")
    void reports_latestSort() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(communityReportRepository).findAll(pageable.capture());
        verify(communityReportRepository, never()).findByStatus(any(), any());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    @DisplayName("신고 목록 status=PENDING → 그 상태로 조회, 정렬은 같다")
    void reports_statusFilter() throws Exception {
        mockMvc.perform(get("/api/admin/reports").param("status", "PENDING"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(communityReportRepository).findByStatus(eq(ReportStatus.PENDING), pageable.capture());
        verify(communityReportRepository, never()).findAll(any(Pageable.class));
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    @DisplayName("잘못된 status → 400 INVALID_INPUT (회원 목록의 잘못된 status와 같은 응답)")
    void reports_invalidStatus() throws Exception {
        mockMvc.perform(get("/api/admin/reports").param("status", "WRONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/admin/members").param("status", "WRONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));

        verifyNoInteractions(communityReportRepository);
    }
}
