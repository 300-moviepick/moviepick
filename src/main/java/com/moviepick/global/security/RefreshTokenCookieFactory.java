package com.moviepick.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키를 만든다. 일반 로그인·재발급·로그아웃·소셜 로그인이 같은 속성을 쓴다.
 * HttpOnly, SameSite=Strict, Path=/api/auth, Secure는 app.cookie.secure(로컬 기본 false).
 */
@Component
public class RefreshTokenCookieFactory {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final boolean secure;

    public RefreshTokenCookieFactory(@Value("${app.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    // 새 Refresh Token을 담은 쿠키 (Max-Age는 토큰 유효기간과 같다)
    public ResponseCookie create(String refreshToken) {
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(JwtProvider.REFRESH_TOKEN_TTL)
                .build();
    }

    // 브라우저의 Refresh Token 쿠키를 지우는 쿠키 (Max-Age=0)
    public ResponseCookie expire() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
