package com.moviepick.domain.member.dto.response;

// Refresh Token 재발급 결과: 새 Access Token과 새 Refresh Token
public record TokenPair(
        String accessToken,
        String refreshToken
) {
}