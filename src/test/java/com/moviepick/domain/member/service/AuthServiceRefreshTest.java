package com.moviepick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moviepick.domain.member.dto.response.TokenPair;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenService;
import io.jsonwebtoken.Claims;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private Claims claims;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        given(jwtProvider.validateRefreshToken("old-token")).willReturn(1L);
        given(jwtProvider.validateToken("old-token")).willReturn(claims);
    }

    @Test
    @DisplayName("재발급하면 새 Access Token과 새 Refresh Token을 주고, 새 Refresh Token을 저장한다")
    void rotate() {
        given(refreshTokenService.consume(1L, "old-token")).willReturn(true);
        given(claims.get("authVersion", Number.class)).willReturn(0L);
        given(refreshTokenService.getAuthVersion(1L)).willReturn(0L);
        given(memberRepository.findById(1L))
                .willReturn(Optional.of(Member.createLocal("a@test.com", "encoded", "user")));
        given(jwtProvider.createRefreshToken(any(), anyLong())).willReturn("new-refresh");
        given(jwtProvider.createAccessToken(any(), anyString())).willReturn("new-access");

        TokenPair tokens = authService.refreshAccessToken("old-token");

        assertThat(tokens.accessToken()).isEqualTo("new-access");
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).save(any(), any());
    }

    @Test
    @DisplayName("이미 사용된 Refresh Token이 다시 오면 401이고 그 회원의 토큰을 모두 무효화한다")
    void reuseDetected() {
        given(refreshTokenService.consume(1L, "old-token")).willReturn(false);

        assertThatThrownBy(() -> authService.refreshAccessToken("old-token"))
                .isInstanceOf(BusinessException.class);

        verify(refreshTokenService).invalidateAll(1L);
        verify(refreshTokenService, never()).save(any(), any());
    }
}