package com.moviepick.domain.member.service;

import com.moviepick.domain.member.dto.request.LoginRequest;
import com.moviepick.domain.member.dto.request.SignupRequest;
import com.moviepick.domain.member.dto.response.LoginResponse;
import com.moviepick.domain.member.dto.response.MemberResponse;
import com.moviepick.domain.member.dto.response.TokenPair;
import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.domain.member.repository.MemberConstraints;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import com.moviepick.domain.member.dto.response.LoginResult;
import com.moviepick.global.security.JwtProvider;
import com.moviepick.global.security.RefreshTokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    // 회원가입
    @Transactional
    public MemberResponse signup(SignupRequest request) {

        // 같은 이메일 + LOCAL 계정 중복 확인
        if (memberRepository.existsByEmailAndProvider(
                request.email(),
                AuthProvider.LOCAL
        )) {
            throw new BusinessException(
                    MemberErrorCode.DUPLICATE_EMAIL
            );
        }

        // 닉네임 앞뒤 공백 제거 후 중복 확인
        String nickname = request.nickname().trim();

        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(
                    MemberErrorCode.DUPLICATE_NICKNAME
            );
        }

        // 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(request.password());

        // LOCAL 회원 생성
        Member member = Member.createLocal(
                request.email(),
                encodedPassword,
                nickname
        );

        try {
            // 즉시 INSERT를 실행해 DB UNIQUE 충돌 확인
            Member savedMember = memberRepository.saveAndFlush(member);

            return MemberResponse.from(savedMember);

        } catch (DataIntegrityViolationException e) {
            // 실제 이메일 + provider 복합 UNIQUE 제약조건 위반인 경우만 처리
            if (isDuplicateEmailConstraintViolation(e)) {
                throw new BusinessException(
                        MemberErrorCode.DUPLICATE_EMAIL
                );
            }

            // 동시 가입으로 닉네임 UNIQUE 제약조건 위반인 경우
            if (MemberConstraints.isDuplicateNickname(e)) {
                throw new BusinessException(
                        MemberErrorCode.DUPLICATE_NICKNAME
                );
            }

            // 그 외 DB 무결성 오류는 중복 이메일로 위장하지 않음
            throw e;
        }
    }

    /**
     * DB에서 발생한 제약조건 위반이 이메일 중복인지 확인한다.
     * 프로젝트의 DB 종류와 드라이버에 따라 예외 메시지가 다를 수 있으므로
     * 실제 운영 환경의 제약조건 정보를 확인해야 한다.
     */
    private boolean isDuplicateEmailConstraintViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            String message = cause.getMessage();

            if (message != null
                    && message.contains("uk_members_email_provider")) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    // 로그인
    @Transactional
    public LoginResult login(LoginRequest request) {

        // 1. 이메일로 회원 조회
        Member member = memberRepository.findByEmailAndProvider(
                        request.email(),
                        AuthProvider.LOCAL
                )
                .orElseThrow(() -> new BusinessException(
                        MemberErrorCode.INVALID_CREDENTIALS
                ));

        // 2. 비밀번호 검증
        if (member.getPasswordHash() == null
                || !passwordEncoder.matches(
                request.password(),
                member.getPasswordHash()
        )) {
            throw new BusinessException(
                    MemberErrorCode.INVALID_CREDENTIALS
            );
        }

        // 3. 정지 기간이 만료되었다면 자동 해제
        member.releaseSuspensionIfExpired();

        // 4. 아직 정지 상태라면 로그인 차단
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new BusinessException(
                    MemberErrorCode.MEMBER_SUSPENDED
            );
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(
                    MemberErrorCode.MEMBER_WITHDRAWN
            );
        }

        // 5. Access Token 생성
        String accessToken = jwtProvider.createAccessToken(
                member.getId(),
                member.getRole().name()
        );

        // 6. 회원의 현재 인증 버전 조회
        long authVersion =
                refreshTokenService.getAuthVersion(member.getId());

        // 7. 인증 버전이 포함된 Refresh Token 생성
        String refreshToken = jwtProvider.createRefreshToken(
                member.getId(),
                authVersion
        );

        // 8. Redis에 Refresh Token 저장
        refreshTokenService.save(
                member.getId(),
                refreshToken
        );

        // 9. 로그인 응답 생성
        LoginResponse loginResponse =
                LoginResponse.of(member, accessToken);

        // 10. Access Token 응답과 Refresh Token 함께 반환
        return new LoginResult(loginResponse, refreshToken);
    }

    // 로그아웃
    public void logout(String refreshToken) {

        // Refresh Token이 없으면 삭제할 토큰도 없으므로 종료
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        // Redis에 저장된 Refresh Token 삭제
        refreshTokenService.delete(refreshToken);
    }

    // Access Token·Refresh Token 재발급 (Refresh Token은 한 번만 사용 가능)
    @Transactional
    public TokenPair refreshAccessToken(String refreshToken) {

        // Refresh Token이 없는 경우 -> 401
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        Long memberId;
        Claims claims;

        // JWT 만료, 서명, 토큰 종류 검증
        try {
            memberId = jwtProvider.validateRefreshToken(refreshToken);
            claims = jwtProvider.validateToken(refreshToken);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        // Redis에 저장된 Refresh Token인지 확인
        if (!refreshTokenService.consume(memberId, refreshToken)) {
            // 서명은 맞는데 저장소에 없음 = 이미 쓴 토큰 재사용 → 탈취 의심, 전부 무효화
            refreshTokenService.invalidateAll(memberId);
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        // 인증 버전 확인
        Number tokenVersion;

        try {
            tokenVersion = claims.get("authVersion", Number.class);
        } catch (RuntimeException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        if (tokenVersion == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        // 현재 인증 버전 조회
        long currentVersion =
                refreshTokenService.getAuthVersion(memberId);

        if (tokenVersion.longValue() != currentVersion) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        // 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.UNAUTHORIZED
                ));

        // 정지 기간이 만료되었다면 자동 해제
        member.releaseSuspensionIfExpired();

        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new BusinessException(
                    MemberErrorCode.MEMBER_SUSPENDED
            );
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(
                    MemberErrorCode.MEMBER_WITHDRAWN
            );
        }

        // 새 Refresh Token 발급 후 저장 (이전 토큰은 consume에서 이미 삭제됨)
        String newRefreshToken = jwtProvider.createRefreshToken(member.getId(), currentVersion);
        refreshTokenService.save(member.getId(), newRefreshToken);

        // 새 Access Token과 새 Refresh Token 반환
        return new TokenPair(
                jwtProvider.createAccessToken(member.getId(), member.getRole().name()),
                newRefreshToken);
    }
}