package com.moviepick.domain.community.controller;

import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.entity.PostStatus;
import com.moviepick.domain.community.repository.PostLikeRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.community.service.PostLikeService;
import com.moviepick.domain.community.service.PostListCacheService;
import com.moviepick.domain.community.service.PostService;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.global.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// REPORT-10: 관리자는 숨긴 게시글 상세를 200으로 본다 (상태 포함). 일반 회원·비로그인은 404
@WebMvcTest(
        controllers = PostController.class,
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
        PostService.class,
        PostLikeService.class,
        PostListCacheService.class
})
@ActiveProfiles("test")
class AdminBlindedPostDetailTest {

    private static final Long BLINDED_ID = 4L;
    private static final Long DELETED_ID = 5L;
    private static final Long VISIBLE_ID = 6L;

    @Autowired
    private MockMvc mockMvc;

    // 실제 서비스를 쓰고 저장소만 모킹한다
    @MockitoBean
    private PostRepository postRepository;
    @MockitoBean
    private PostLikeRepository postLikeRepository;
    @MockitoBean
    private MemberRepository memberRepository;

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

    @BeforeEach
    void setUp() {
        Member writer = Member.createLocal("w@test.com", "encoded", "writer");
        ReflectionTestUtils.setField(writer, "id", 10L);

        givenPost(BLINDED_ID, writer, PostStatus.BLINDED);
        givenPost(DELETED_ID, writer, PostStatus.DELETED);
        givenPost(VISIBLE_ID, writer, PostStatus.VISIBLE);
    }

    private void givenPost(Long id, Member writer, PostStatus status) {
        Post post = Post.create(writer, "title" + id, "content");
        ReflectionTestUtils.setField(post, "id", id);
        ReflectionTestUtils.setField(post, "status", status);

        given(postRepository.findById(id)).willReturn(Optional.of(post));
        given(postRepository.findByIdAndStatus(id, PostStatus.VISIBLE))
                .willReturn(status == PostStatus.VISIBLE ? Optional.of(post) : Optional.empty());
    }

    private MockHttpServletRequestBuilder detailAs(Long postId, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return get("/api/community/posts/{postId}", postId).with(authentication(auth));
    }

    @Test
    @DisplayName("관리자: 숨긴 글 상세 → 200, status=BLINDED, 조회수 증가 없음")
    void admin_blinded_ok() throws Exception {
        mockMvc.perform(detailAs(BLINDED_ID, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(BLINDED_ID))
                .andExpect(jsonPath("$.data.status").value("BLINDED"));

        verify(postRepository, never()).increaseViewCount(anyLong());
    }

    @Test
    @DisplayName("관리자: 삭제된 글 상세 → 404 POST_NOT_FOUND")
    void admin_deleted_404() throws Exception {
        mockMvc.perform(detailAs(DELETED_ID, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("일반 회원: 숨긴 글 상세 → 404 POST_NOT_FOUND")
    void user_blinded_404() throws Exception {
        mockMvc.perform(detailAs(BLINDED_ID, "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("비로그인: 숨긴 글 상세 → 404 POST_NOT_FOUND")
    void anonymous_blinded_404() throws Exception {
        mockMvc.perform(get("/api/community/posts/{postId}", BLINDED_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("공개 글 상세 → 200, status=VISIBLE")
    void visible_ok() throws Exception {
        mockMvc.perform(get("/api/community/posts/{postId}", VISIBLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VISIBLE"));
    }
}