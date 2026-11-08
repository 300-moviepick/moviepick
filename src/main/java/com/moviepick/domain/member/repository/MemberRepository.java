package com.moviepick.domain.member.repository;

import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 신규 기능: 이메일과 가입 방식으로 회원 조회
    // LOCAL 로그인 시 다른 소셜 계정과 구분
    Optional<Member> findByEmailAndProvider(
            String email,
            AuthProvider provider
    );

    // 신규 기능: 동일 가입 방식에서 이메일 중복 확인
    boolean existsByEmailAndProvider(
            String email,
            AuthProvider provider
    );

    // 신규 기능: 닉네임 중복 확인 (DB collation 기준으로 비교)
    boolean existsByNickname(String nickname);

    // 신규 기능: 자신을 뺀 닉네임 중복 확인 (닉네임 수정)
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    // 기존 기능: 카카오·구글 회원 조회
    Optional<Member> findByProviderAndProviderId(
            AuthProvider provider,
            String providerId
    );

    // 기존 기능: 관리자 회원 상태별 목록 조회
    Page<Member> findByStatus(
            MemberStatus status,
            Pageable pageable
    );

    // 기존 기능: 관리자 제재 처리 시 회원 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForUpdate(
            @Param("memberId") Long memberId
    );
}
