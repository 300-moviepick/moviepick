package com.moviepick.domain.movie.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.movie.dto.response.MovieListResponse;
import com.moviepick.domain.movie.service.MovieExtraService;
import com.moviepick.domain.movie.service.MovieQueryService;
import com.moviepick.domain.movie.service.MovieReviewReader;
import com.moviepick.global.security.CustomAccessDeniedHandler;
import com.moviepick.global.security.CustomAuthenticationEntryPoint;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.SecurityConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// GET /api/movies sort 파라미터: 없으면 기존 id 내림차순, releaseDate면 최근 개봉작 조회, 그 외 값은 400 INVALID_INPUT
@WebMvcTest(
        controllers = MovieController.class,
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
class MovieControllerSortTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieQueryService movieQueryService;
    @MockitoBean
    private MovieReviewReader movieReviewReader;
    @MockitoBean
    private MovieExtraService movieExtraService;

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

    private static final List<MovieListResponse> MOVIES = List.of(
            new MovieListResponse(2L, "kr", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 10),
                    null, null, null, 0),
            new MovieListResponse(1L, "noKr", LocalDate.of(2026, 10, 1), null,
                    null, null, null, 0));

    @BeforeEach
    void setUp() {
        given(movieQueryService.getMovies(any(), any(Pageable.class)))
                .willAnswer(inv -> new PageImpl<>(MOVIES, inv.getArgument(1, Pageable.class), MOVIES.size()));
        given(movieQueryService.getRecentlyReleasedMovies(any(), any(Pageable.class)))
                .willAnswer(inv -> new PageImpl<>(MOVIES, inv.getArgument(1, Pageable.class), MOVIES.size()));
    }

    @Test
    @DisplayName("sort 없음 → 기존 getMovies를 id 내림차순 Pageable로 호출한다")
    void noSortKeepsIdDesc() throws Exception {
        mockMvc.perform(get("/api/movies").param("keyword", "abc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(movieQueryService).getMovies(eq("abc"), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        verify(movieQueryService, never()).getRecentlyReleasedMovies(any(), any());
    }

    @Test
    @DisplayName("sort=releaseDate·RELEASEDATE → 최근 개봉작 조회를 호출하고 page·size를 그대로 넘긴다")
    void releaseDateSort() throws Exception {
        mockMvc.perform(get("/api/movies").param("sort", "releaseDate").param("size", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(8))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/api/movies").param("sort", "RELEASEDATE").param("keyword", "abc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(movieQueryService).getRecentlyReleasedMovies(isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(8);
        verify(movieQueryService).getRecentlyReleasedMovies(eq("abc"), any(Pageable.class));
        verify(movieQueryService, never()).getMovies(any(), any());
    }

    @Test
    @DisplayName("허용되지 않은 sort 값 → 400 INVALID_INPUT, 서비스를 호출하지 않는다")
    void invalidSort() throws Exception {
        mockMvc.perform(get("/api/movies").param("sort", "title"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));

        verifyNoInteractions(movieQueryService);
    }

    @Test
    @DisplayName("응답 목록에 releaseDateKr이 포함되고, 한국 개봉일이 없으면 null로 내려간다")
    void responseIncludesReleaseDateKr() throws Exception {
        String body = mockMvc.perform(get("/api/movies").param("sort", "releaseDate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].releaseDateKr").value("2026-10-10"))
                .andExpect(jsonPath("$.data.content[0].releaseDate").value("2026-09-01"))
                .andReturn().getResponse().getContentAsString();

        JsonNode noKr = objectMapper.readTree(body).path("data").path("content").path(1);
        assertThat(noKr.has("releaseDateKr")).isTrue();
        assertThat(noKr.get("releaseDateKr").isNull()).isTrue();
    }
}
