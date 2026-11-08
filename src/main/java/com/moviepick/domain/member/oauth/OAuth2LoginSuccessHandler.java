package com.moviepick.domain.member.oauth;

import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.FrontendProperties;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenCookieFactory;
import com.moviepick.global.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final FrontendProperties frontendProperties;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        // 1. 카카오인지 구글인지 확인
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            redirectToFail(response, OAuth2LoginFailureHandler.REASON_OAUTH2_FAILED);
            return;
        }

        String registrationId =
                oauthToken.getAuthorizedClientRegistrationId();

        OAuth2User oauth2User = oauthToken.getPrincipal();

        // 2. 로그인 제공자별 회원 식별자 추출
        AuthProvider provider;
        String providerId;

        if ("kakao".equals(registrationId)) {

            provider = AuthProvider.KAKAO;

            Object kakaoId = oauth2User.getAttributes().get("id");

            if (kakaoId == null) {
                redirectToFail(response, OAuth2LoginFailureHandler.REASON_OAUTH2_FAILED);
                return;
            }

            providerId = String.valueOf(kakaoId);

        } else if ("google".equals(registrationId)) {

            provider = AuthProvider.GOOGLE;

            Object googleId = oauth2User.getAttributes().get("sub");

            if (googleId == null || String.valueOf(googleId).isBlank()) {
                redirectToFail(response, OAuth2LoginFailureHandler.REASON_OAUTH2_FAILED);
                return;
            }

            providerId = String.valueOf(googleId);

        } else {

            redirectToFail(response, OAuth2LoginFailureHandler.REASON_OAUTH2_FAILED);
            return;
        }

        // 3. DB에서 소셜 회원 조회
        Member member = memberRepository
                .findByProviderAndProviderId(provider, providerId)
                .orElse(null);

        if (member == null) {
            redirectToFail(response, OAuth2LoginFailureHandler.REASON_OAUTH2_FAILED);
            return;
        }

        // 정지·탈퇴 회원은 구분할 수 있는 reason으로 실패 화면 이동
        if (member.getStatus() != MemberStatus.ACTIVE) {
            redirectToFail(response, OAuth2LoginFailureHandler.reasonOf(member.getStatus()));
            return;
        }

        // 4. 회원의 현재 인증 버전 조회
        long authVersion = refreshTokenService.getAuthVersion(
                member.getId()
        );

        // 5. Refresh Token 생성
        String refreshToken = jwtProvider.createRefreshToken(
                member.getId(),
                authVersion
        );

        // 6. Redis에 Refresh Token 저장
        refreshTokenService.save(
                member.getId(),
                refreshToken
        );

        // 7. Refresh Token을 HttpOnly 쿠키로 발급
        ResponseCookie cookie = refreshTokenCookieFactory.create(refreshToken);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );

        // 8. OAuth2 인증 과정에서 사용한 세션 정리
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();


        // 9. React 로그인 완료 페이지로 이동
        response.sendRedirect(frontendProperties.oauth2SuccessUrl());

    }

    // 프론트 소셜 로그인 실패 화면으로 이동
    private void redirectToFail(
            HttpServletResponse response,
            String reason
    ) throws IOException {

        response.sendRedirect(frontendProperties.oauth2FailUrl(reason));
    }
}
