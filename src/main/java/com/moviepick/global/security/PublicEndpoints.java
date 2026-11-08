package com.moviepick.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 로그인 없이 쓸 수 있는 공개 주소 목록.
 * SecurityConfig의 permitAll과 JWT 필터의 공개 주소 판단이 모두 이 목록 하나를 쓴다.
 */
public final class PublicEndpoints {

    private static final List<RequestMatcher> MATCHERS = new ArrayList<>();

    static {
        // CSRF 토큰 발급
        add(HttpMethod.GET, "/api/auth/csrf");

        // 회원가입 및 로그인
        add(HttpMethod.POST, "/api/auth/members", "/api/auth/login");

        // 로그아웃 및 토큰 재발급
        add(HttpMethod.POST, "/api/auth/logout", "/api/auth/refresh");

        // 영화 목록, 상세, 랭킹(/api/movies/ranking은 /api/movies/*에 포함) 조회
        add(HttpMethod.GET, "/api/movies", "/api/movies/*");

        // 영화별 리뷰 목록 및 전체 리뷰 목록 조회
        add(HttpMethod.GET, "/api/movies/*/reviews", "/api/movies/reviews");

        // 커뮤니티 게시글 및 댓글 조회
        add(HttpMethod.GET,
                "/api/community/posts",
                "/api/community/posts/*",
                "/api/community/posts/*/comments");

        // 영화 확장 정보(예고편·출연진·OTT) 조회
        add(HttpMethod.GET, "/api/movies/*/extras");

        // PortOne 결제 웹훅 (서명으로 검증, CSRF 예외는 SecurityConfig에서 따로 등록)
        add(HttpMethod.POST, "/api/webhooks/portone");
    }

    public static final RequestMatcher MATCHER = new OrRequestMatcher(MATCHERS);

    private PublicEndpoints() {
    }

    public static boolean matches(HttpServletRequest request) {
        return MATCHER.matches(request);
    }

    private static void add(HttpMethod method, String... patterns) {
        for (String pattern : patterns) {
            MATCHERS.add(PathPatternRequestMatcher.withDefaults().matcher(method, pattern));
        }
    }
}
