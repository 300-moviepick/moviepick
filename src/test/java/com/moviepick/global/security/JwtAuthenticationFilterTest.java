package com.moviepick.global.security;

import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.community.controller.PostController;
import com.moviepick.domain.community.service.PostListCacheService;
import com.moviepick.domain.community.service.PostService;
import com.moviepick.domain.member.controller.MemberController;
import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.movie.controller.MovieController;
import com.moviepick.domain.movie.service.MovieQueryService;
import com.moviepick.domain.movie.service.MovieExtraService;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.global.config.FilterErrorConfig;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.domain.member.controller.AuthController;
import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.domain.member.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AUTH-03: 공개 주소는 잘못된 토큰을 무시하고, 보호 주소는 401·403으로 응답한다
@WebMvcTest(
        controllers = {
                MovieController.class,
                PostController.class,
                MemberController.class,
                AuthController.class
        },
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
        FilterErrorConfig.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class,
        MemberStatusChecker.class,
        OAuth2LoginFailureHandler.class
})
@ActiveProfiles("test")
class JwtAuthenticationFilterTest {

    private static final String EXPIRED = "expired-token";
    private static final String TAMPERED = "tampered-token";
    private static final String SUSPENDED = "suspended-member-token";
    private static final String WITHDRAWN = "withdrawn-member-token";
    private static final String UNKNOWN = "unknown-member-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MovieQueryService movieQueryService;
    @MockitoBean
    private MovieExtraService movieExtraService;
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private PostService postService;
    @MockitoBean
    private PostListCacheService postListCacheService;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private CustomOidcUserService customOidcUserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @BeforeEach
    void setUp() {
        given(jwtProvider.validateToken(EXPIRED))
                .willThrow(new IllegalArgumentException("유효하지 않은 토큰입니다."));
        given(jwtProvider.validateToken(TAMPERED))
                .willThrow(new IllegalArgumentException("유효하지 않은 토큰입니다."));
        givenMember(SUSPENDED, 20L, MemberErrorCode.MEMBER_SUSPENDED);
        givenMember(WITHDRAWN, 21L, MemberErrorCode.MEMBER_WITHDRAWN);
        givenMember(UNKNOWN, 22L, MemberErrorCode.MEMBER_NOT_FOUND);

        given(movieQueryService.getMovies(any(), any())).willReturn(Page.empty());
        given(postService.getPosts(any(), anyString(), any())).willReturn(Page.empty());
        given(reviewService.findMyReviewId(any(), any())).willReturn(Optional.empty());
    }

    private void givenMember(String token, Long memberId, MemberErrorCode errorCode) {
        Claims claims = Jwts.claims().subject(memberId.toString()).add("type", "access").build();
        given(jwtProvider.validateToken(token)).willReturn(claims);
        given(memberService.getActiveMember(memberId)).willThrow(new BusinessException(errorCode));
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    // ===== 공개 주소: 토큰을 무시하고 비로그인처럼 응답 =====

    @Test
    @DisplayName("공개 주소에 만료 토큰 → 200")
    void public_expired_ok() throws Exception {
        getWithToken("/api/movies", EXPIRED).andExpect(status().isOk());
        getWithToken("/api/community/posts", EXPIRED).andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 주소에 서명 틀린 토큰 → 200")
    void public_tampered_ok() throws Exception {
        getWithToken("/api/movies", TAMPERED).andExpect(status().isOk());
        getWithToken("/api/community/posts", TAMPERED).andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 주소에 정지·탈퇴·없는 회원 토큰 → 200")
    void public_inactiveMember_ok() throws Exception {
        getWithToken("/api/movies", SUSPENDED).andExpect(status().isOk());
        getWithToken("/api/movies", WITHDRAWN).andExpect(status().isOk());
        getWithToken("/api/community/posts", UNKNOWN).andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 주소에 정지 회원 토큰 → 비로그인으로 처리 (회원 ID 없음)")
    void public_suspended_anonymous() throws Exception {
        getWithToken("/api/movies/1", SUSPENDED).andExpect(status().isOk());

        verify(reviewService).findMyReviewId(isNull(), eq(1L));
    }

    @Test
    @DisplayName("공개 주소에 Bearer 형식이 아닌 헤더 → 200")
    void public_notBearer_ok() throws Exception {
        mockMvc.perform(get("/api/movies").header("Authorization", "Basic abc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("만료 토큰으로 로그아웃 → 200, refresh 쿠키 삭제")
    void logout_expired_ok() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + EXPIRED)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    // ===== 보호 주소: 401·403 =====

    @Test
    @DisplayName("보호 주소에 만료·서명 틀린 토큰 → 401 UNAUTHORIZED")
    void protected_invalidToken_401() throws Exception {
        getWithToken("/api/members/me", EXPIRED)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        getWithToken("/api/members/me", TAMPERED)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("보호 주소에 정지 회원 토큰 → 403 MEMBER_SUSPENDED")
    void protected_suspended_403() throws Exception {
        getWithToken("/api/members/me", SUSPENDED)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_SUSPENDED"))
                .andExpect(jsonPath("$.message").value(MemberErrorCode.MEMBER_SUSPENDED.getMessage()));
    }

    @Test
    @DisplayName("보호 주소에 탈퇴 회원 토큰 → 403 MEMBER_WITHDRAWN")
    void protected_withdrawn_403() throws Exception {
        getWithToken("/api/members/me", WITHDRAWN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_WITHDRAWN"));
    }

    @Test
    @DisplayName("보호 주소에 없는 회원 토큰 → 401 UNAUTHORIZED")
    void protected_unknownMember_401() throws Exception {
        getWithToken("/api/members/me", UNKNOWN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
}