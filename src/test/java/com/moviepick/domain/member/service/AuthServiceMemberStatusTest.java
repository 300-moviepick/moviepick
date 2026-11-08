package com.moviepick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.moviepick.domain.member.dto.request.LoginRequest;
import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.port.SignupBenefitPort;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenService;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 로그인·토큰 재발급의 회원 상태 검사: 만료된 정지는 해제하고, 정지·탈퇴 회원은 403 (R10 정리 전후 동일해야 함) */
@ExtendWith(MockitoExtension.class)
class AuthServiceMemberStatusTest {

    private static final String EMAIL = "status@test.com";
    private static final String PASSWORD = "password123!";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SignupBenefitPort signupBenefitPort;
    @Mock private Claims claims;
    @InjectMocks private AuthService authService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.createLocal(EMAIL, "encoded-password", "상태회원");
        // 성공하는 경우에만 쓰이는 토큰 발급 스텁
        lenient().when(jwtProvider.createAccessToken(any(), anyString())).thenReturn("access");
        lenient().when(jwtProvider.createRefreshToken(any(), anyLong())).thenReturn("new-refresh");
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @BeforeEach
        void setUp() {
            given(memberRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL))
                    .willReturn(Optional.of(member));
            given(passwordEncoder.matches(PASSWORD, "encoded-password")).willReturn(true);
        }

        @Test
        void 정지_기간이_남았으면_MEMBER_SUSPENDED() {
            member.suspend(LocalDateTime.now().plusDays(1));

            assertErrorCode(this::login, MemberErrorCode.MEMBER_SUSPENDED);
        }

        @Test
        void 탈퇴_회원이면_MEMBER_WITHDRAWN() {
            member.withdraw();

            assertErrorCode(this::login, MemberErrorCode.MEMBER_WITHDRAWN);
        }

        @Test
        void 정지_기간이_지났으면_해제하고_로그인한다() {
            member.suspend(LocalDateTime.now().minusMinutes(1));

            login();

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        }

        private void login() {
            authService.login(new LoginRequest(EMAIL, PASSWORD));
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Refresh {

        @BeforeEach
        void setUp() {
            given(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).willReturn(1L);
            given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(claims);
            given(refreshTokenService.consume(1L, REFRESH_TOKEN)).willReturn(true);
            given(claims.get("authVersion", Number.class)).willReturn(0L);
            given(refreshTokenService.getAuthVersion(1L)).willReturn(0L);
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        }

        @Test
        void 정지_기간이_남았으면_MEMBER_SUSPENDED() {
            member.suspend(LocalDateTime.now().plusDays(1));

            assertErrorCode(this::refresh, MemberErrorCode.MEMBER_SUSPENDED);
        }

        @Test
        void 탈퇴_회원이면_MEMBER_WITHDRAWN() {
            member.withdraw();

            assertErrorCode(this::refresh, MemberErrorCode.MEMBER_WITHDRAWN);
        }

        @Test
        void 정지_기간이_지났으면_해제하고_재발급한다() {
            member.suspend(LocalDateTime.now().minusMinutes(1));

            refresh();

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        }

        private void refresh() {
            authService.refreshAccessToken(REFRESH_TOKEN);
        }
    }

    private static void assertErrorCode(Runnable call, MemberErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
