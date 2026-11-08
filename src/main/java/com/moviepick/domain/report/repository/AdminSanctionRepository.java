package com.moviepick.domain.report.repository;

import com.moviepick.domain.report.entity.AdminSanction;
import com.moviepick.domain.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdminSanctionRepository
        extends JpaRepository<AdminSanction, Long> {

    // 특정 회원에게 적용된 누적 제재 횟수 조회
    long countByMemberId(Long memberId);

    // 동일 게시글 또는 댓글에 이미 제재가 적용되었는지 확인
    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM AdminSanction s
            JOIN s.report r
            WHERE r.targetType = :targetType
              AND r.targetId = :targetId
            """)
    boolean existsByReportTarget(
            @Param("targetType") ReportTargetType targetType,
            @Param("targetId") Long targetId
    );

    // 특정 회원의 제재 이력 전체 조회
    List<AdminSanction> findAllByMemberId(Long memberId);

}
