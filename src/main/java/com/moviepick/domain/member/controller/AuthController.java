package com.moviepick.domain.member.controller;

import com.moviepick.domain.member.dto.request.LoginRequest;
import com.moviepick.domain.member.dto.request.SignupRequest;
import com.moviepick.domain.member.dto.response.LoginResponse;
import com.moviepick.domain.member.dto.response.MemberResponse;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.domain.member.dto.response.LoginResult;
import com.moviepick.domain.member.dto.response.TokenRefreshResponse;
import com.moviepick.domain.member.dto.response.TokenPair;
import com.moviepick.domain.member.service.AuthService;
import com.moviepick.global.security.RefreshTokenCookieFactory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    // 회원가입 API
    @PostMapping("/members")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        MemberResponse response = authService.signup(request);

        return ResponseEntity
                .status(201)
                .body(ApiResponse.created(response));
    }

    // 로그인 API
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {

        // 1. 로그인 처리
        LoginResult result = authService.login(request);

        // 2. Refresh Token 쿠키 생성
        ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());

        // 3. Access Token은 JSON 응답으로 전달
        // Refresh Token은 HttpOnly 쿠키로 전달
        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .body(
                        ApiResponse.ok(result.loginResponse())
                );
    }

    // 로그아웃 API
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(
                    value = "refreshToken",
                    required = false
            ) String refreshToken
    ) {

        // 1. Redis에 저장된 Refresh Token 삭제
        authService.logout(refreshToken);

        // 2. 브라우저의 Refresh Token 쿠키 만료
        ResponseCookie deleteCookie = refreshTokenCookieFactory.expire();

        // 3. 쿠키 삭제 응답 반환
        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        deleteCookie.toString()
                )
                .body(ApiResponse.ok());
    }

    // Access Token 재발급 API (Refresh Token도 새로 발급해 쿠키를 교체)
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @CookieValue(
                    value = "refreshToken",
                    required = false
            ) String refreshToken
    ) {

        // 1. Refresh Token 검증 후 새 토큰 2개 발급 (기존 Refresh Token은 폐기)
        TokenPair tokens = authService.refreshAccessToken(refreshToken);

        // 2. 새 Access Token은 JSON, 새 Refresh Token은 HttpOnly 쿠키로 전달
        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieFactory.create(tokens.refreshToken()).toString()
                )
                .body(
                        ApiResponse.ok(new TokenRefreshResponse(tokens.accessToken()))
                );
    }
}
