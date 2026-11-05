package com.moviepick.global.response;

import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.Role;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Date;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "JWT_SECRET=" + ApiCommonResponseTest.SECRET,
        "TMDB_ACCESS_TOKEN=test-dummy-tmdb-token"
})
@AutoConfigureMockMvc
class ApiCommonResponseTest {

    static final String SECRET = "bW92aWVwaWNrLXRlc3Qtb25seS1qd3Qtc2VjcmV0LWtleS1mb3ItY2khIQ==";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired MemberRepository memberRepository;
    @Autowired MovieRepository movieRepository;
    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;

    // 로그인·연장 토큰 저장소(Redis)는 이 테스트에서 쓰지 않는다
    @MockitoBean RefreshTokenService refreshTokenService;

    Member user;
    Member admin;
    String userToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        // 테스트마다 회원을 새로 저장하므로 이메일·닉네임(둘 다 유니크)을 매번 다르게 만든다
        long seq = System.nanoTime();
        user = memberRepository.save(Member.createLocal(
                "user" + seq + "@test.com", "x", "user" + seq));
        Member newAdmin = Member.createLocal("admin" + seq + "@test.com", "x", "admin" + seq);
        ReflectionTestUtils.setField(newAdmin, "role", Role.ADMIN);
        admin = memberRepository.save(newAdmin);

        userToken = "Bearer " + jwtProvider.createAccessToken(user.getId(), "USER");
        adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), "ADMIN");
    }

    private ResultActions expectError(ResultActions result, int status, String errorCode) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(status))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    @Test
    @DisplayName("성공 응답에는 errorCode가 없다")
    void success_hasNoErrorCode() throws Exception {
        Movie movie = movieRepository.save(new Movie(System.nanoTime(), "movie"));

        mvc.perform(get("/api/movies/" + movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.movieId").value(movie.getId()))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    @DisplayName("Page를 그대로 반환하던 목록도 PageResponse 모양으로 감싼다")
    void rawPage_isWrappedAsPageResponse() throws Exception {
        mvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.totalPages").isNumber())
                .andExpect(jsonPath("$.data.pageable").doesNotExist());

        mvc.perform(get("/api/admin/members").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    @DisplayName("CSRF 토큰 발급 응답도 공통 모양으로 감싼다")
    void csrf_isWrapped() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    @DisplayName("본문 없는 삭제 성공(204)은 200 + 공통 모양으로 바꾼다")
    void noContentDelete_becomesOk() throws Exception {
        Post post = postRepository.save(Post.create(user, "title", "content"));
        Comment comment = commentRepository.save(Comment.create(post, user, "comment"));

        // 관리자 댓글 삭제 API가 숨김(PATCH)으로 바뀌어, 본문 없는 DELETE인 작성자 댓글 삭제로 확인한다
        mvc.perform(delete("/api/community/comments/" + comment.getId())
                        .with(csrf())
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("없는 영화는 404 MOVIE_NOT_FOUND (상세와 리뷰가 같은 이름)")
    void missingMovie_404() throws Exception {
        expectError(mvc.perform(get("/api/movies/999999")), 404, "MOVIE_NOT_FOUND");
    }

    @Test
    @DisplayName("없는 주소는 404 PATH_NOT_FOUND")
    void unknownPath_404() throws Exception {
        expectError(mvc.perform(get("/api/nothing").header("Authorization", userToken)),
                404, "PATH_NOT_FOUND");
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED")
    void wrongMethod_405() throws Exception {
        expectError(mvc.perform(put("/api/community/posts/1")
                        .with(csrf())
                        .header("Authorization", userToken)
                        .contentType("application/json")
                        .content("{}")),
                405, "METHOD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("JSON이 아닌 본문은 415 UNSUPPORTED_MEDIA_TYPE")
    void wrongContentType_415() throws Exception {
        expectError(mvc.perform(post("/api/community/posts")
                        .with(csrf())
                        .header("Authorization", userToken)
                        .contentType("text/plain")
                        .content("x")),
                415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("깨진 JSON은 400 INVALID_INPUT")
    void brokenJson_400() throws Exception {
        expectError(mvc.perform(post("/api/community/posts")
                        .with(csrf())
                        .header("Authorization", userToken)
                        .contentType("application/json")
                        .content("{\"title\":")),
                400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("페이지 크기 100 초과, 음수 페이지, 숫자가 아닌 값은 400 INVALID_PAGE_REQUEST")
    void pageRules_400() throws Exception {
        expectError(mvc.perform(get("/api/community/posts").param("size", "101")), 400, "INVALID_PAGE_REQUEST");
        expectError(mvc.perform(get("/api/movies").param("size", "100000")), 400, "INVALID_PAGE_REQUEST");
        expectError(mvc.perform(get("/api/movies/reviews").param("size", "0")), 400, "INVALID_PAGE_REQUEST");
        expectError(mvc.perform(get("/api/admin/reports").param("page", "-1")
                .header("Authorization", adminToken)), 400, "INVALID_PAGE_REQUEST");
        expectError(mvc.perform(get("/api/community/posts").param("page", "abc")), 400, "INVALID_PAGE_REQUEST");

        mvc.perform(get("/api/community/posts").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("관리자 영화 불러오기의 pages 파라미터는 페이지 규칙 검사 대상이 아니다")
    void pagesParam_isNotPageRule() throws Exception {
        // pages=5는 허용(최대 5), size 규칙과 무관하게 통과해야 한다 — 6이면 해당 API의 검증(400 INVALID_INPUT)
        expectError(mvc.perform(post("/api/admin/movies/sync").param("pages", "6")
                        .with(csrf())
                        .header("Authorization", adminToken)),
                400, "INVALID_INPUT");
    }

    @Test
    @DisplayName("로그인 없이 보호된 API는 401 UNAUTHORIZED")
    void noToken_401() throws Exception {
        expectError(mvc.perform(get("/api/members/me")), 401, "UNAUTHORIZED");
    }

    @Test
    @DisplayName("CSRF 토큰 없이 변경 요청은 403 CSRF_INVALID")
    void noCsrf_403() throws Exception {
        expectError(mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"a@test.com\",\"password\":\"12345678\"}")),
                403, "CSRF_INVALID");
    }

    @Test
    @DisplayName("일반 회원이 관리자 API를 부르면 403 FORBIDDEN")
    void userOnAdmin_403() throws Exception {
        expectError(mvc.perform(get("/api/admin/reports").header("Authorization", userToken)),
                403, "FORBIDDEN");
    }

    @Test
    @DisplayName("필터 단계의 토큰 오류도 401 공통 모양이고 CORS 헤더가 남는다")
    void filterJwtError_401_keepsCors() throws Exception {
        // type 값이 문자열이 아닌 토큰 → JWT 필터 밖으로 JwtException이 나간다
        String badTypeToken = Jwts.builder()
                .subject(user.getId().toString())
                .claim("type", 123)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        expectError(mvc.perform(get("/api/members/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Authorization", "Bearer " + badTypeToken)),
                401, "UNAUTHORIZED")
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
