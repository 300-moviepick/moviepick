package com.moviepick.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

// AUTH-03 준비: 공개 주소 목록을 PublicEndpoints로 옮긴 뒤에도 기존과 같은 주소만 공개인지 확인
class PublicEndpointsTest {

    @ParameterizedTest(name = "{0} {1} → 공개 {2}")
    @DisplayName("기존 SecurityConfig 공개 목록과 같은 주소만 공개")
    @CsvSource({
            // 공개 주소
            "GET, /api/auth/csrf, true",
            "POST, /api/auth/members, true",
            "POST, /api/auth/login, true",
            "POST, /api/auth/logout, true",
            "POST, /api/auth/refresh, true",
            "GET, /api/movies, true",
            "GET, /api/movies/1, true",
            "GET, /api/movies/1/reviews, true",
            "GET, /api/movies/reviews, true",
            "GET, /api/community/posts, true",
            "GET, /api/community/posts/1, true",
            "GET, /api/community/posts/1/comments, true",
            "GET, /api/community/posts/1/likes, true",
            "GET, /api/movies/1/extras, true",
            "GET, /api/movies/ranking, true",
            "POST, /api/webhooks/portone, false",
            // 같은 주소라도 메서드가 다르면 보호
            "POST, /api/movies/1/reviews, false",
            "POST, /api/community/posts, false",
            "PATCH, /api/community/posts/1, false",
            "GET, /api/auth/login, false",
            // 보호 주소
            "GET, /api/members/me, false",
            "GET, /api/members/me/reviews, false",
            "GET, /api/admin/reports, false",
            "POST, /api/reviews/1/likes, false"
    })
    void matches(String method, String path, boolean expected) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);

        assertThat(PublicEndpoints.matches(request)).isEqualTo(expected);
    }
}
