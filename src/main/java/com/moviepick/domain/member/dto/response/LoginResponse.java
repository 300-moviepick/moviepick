package com.moviepick.domain.member.dto.response;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.Role;
import com.moviepick.global.security.JwtProvider;

public record LoginResponse(
        Long memberId,
        String nickname,
        Role role,
        String accessToken,
        String tokenType,
        int expiresIn
) {

    public static LoginResponse of(
            Member member,
            String accessToken
    ) {
        return new LoginResponse(
                member.getId(),
                member.getNickname(),
                member.getRole(),
                accessToken,
                "Bearer",
                (int) JwtProvider.ACCESS_TOKEN_TTL.toSeconds()
        );
    }
}