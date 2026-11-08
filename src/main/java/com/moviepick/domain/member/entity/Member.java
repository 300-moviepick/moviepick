package com.moviepick.domain.member.entity;

import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.global.entity.BaseEntity;
import com.moviepick.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

@Getter
@Entity
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_members_email_provider",
                        columnNames = {"email", "provider"}
                ),
                @UniqueConstraint(
                        name = "uk_members_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                ),
                @UniqueConstraint(
                        name = "uk_members_nickname",
                        columnNames = {"nickname"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    // 탈퇴 회원의 닉네임·이메일·소셜 키에 쓰는 값. 일반 가입·수정에서는 쓸 수 없다.
    private static final String WITHDRAWN_PREFIX = "withdrawn_";
    private static final String WITHDRAWN_EMAIL_DOMAIN = "@withdrawn.local";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    public static Member createLocal(
            String email,
            String passwordHash,
            String nickname
    ) {
        Member member = new Member();
        member.provider = AuthProvider.LOCAL;
        member.email = email;
        member.passwordHash = passwordHash;
        member.nickname = nickname;
        member.role = Role.USER;
        member.status = MemberStatus.ACTIVE;
        return member;
    }

    public static Member createSocial(
            AuthProvider provider,
            String providerId,
            String email,
            String nickname
    ) {
        if (provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException(
                    "소셜 회원은 카카오 또는 구글 제공자를 사용해야 합니다."
            );
        }

        Member member = new Member();
        member.provider = provider;
        member.providerId = providerId;
        member.email = email;
        member.nickname = nickname.length() > 50
                ? nickname.substring(0, 50)
                : nickname;
        member.role = Role.USER;
        member.status = MemberStatus.ACTIVE;
        return member;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void suspend(LocalDateTime until) {
        this.status = MemberStatus.SUSPENDED;
        this.suspendedUntil = until;
    }

    public void releaseSuspension() {
        this.status = MemberStatus.ACTIVE;
        this.suspendedUntil = null;
    }

    // 탈퇴: 이메일·닉네임·소셜 키를 회원번호 기반 값으로 바꿔 같은 값으로 다시 가입할 수 있게 한다 (B5·B6)
    public void withdraw() {
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
        this.email = WITHDRAWN_PREFIX + this.id + WITHDRAWN_EMAIL_DOMAIN;
        this.nickname = WITHDRAWN_PREFIX + this.id;

        if (this.provider != AuthProvider.LOCAL) {
            this.providerId = WITHDRAWN_PREFIX + this.id;
        }
    }

    // 탈퇴 회원용 닉네임과 같아질 수 있는 값인지 (가입·수정에서 거절)
    public static boolean isReservedNickname(String nickname) {
        return nickname != null
                && normalizeForReservedCheck(nickname.trim()).startsWith(WITHDRAWN_PREFIX);
    }

    // 탈퇴 회원용 이메일 도메인인지 (가입에서 거절)
    public static boolean isReservedEmail(String email) {
        return email != null
                && normalizeForReservedCheck(email.trim()).endsWith(WITHDRAWN_EMAIL_DOMAIN);
    }

    // DB collation(utf8mb4_0900_ai_ci)이 같게 보는 대소문자·악센트·전각 차이를 없앤다
    private static String normalizeForReservedCheck(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }


    public boolean releaseSuspensionIfExpired() {

        // 정지 상태가 아니면 아무 작업도 하지 않음
        if (this.status != MemberStatus.SUSPENDED) {
            return false;
        }

        // 종료일이 없으면 영구 정지이므로 해제하지 않음
        if (this.suspendedUntil == null) {
            return false;
        }

        // 정지 종료 시간이 지났다면 자동 해제
        if (!this.suspendedUntil.isAfter(LocalDateTime.now())) {
            releaseSuspension();
            return true;
        }

        return false;
    }

    // 로그인·토큰 재발급·인증 요청 공통: 만료된 정지는 먼저 해제하고, 그래도 정지·탈퇴 상태면 403
    public void releaseExpiredSuspensionAndRequireActive() {
        releaseSuspensionIfExpired();

        if (this.status == MemberStatus.SUSPENDED) {
            throw new BusinessException(MemberErrorCode.MEMBER_SUSPENDED);
        }

        if (this.status == MemberStatus.WITHDRAWN) {
            throw new BusinessException(MemberErrorCode.MEMBER_WITHDRAWN);
        }
    }
}
