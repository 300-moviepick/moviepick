package com.moviepick.domain.report.repository;

import com.moviepick.domain.report.entity.CommunityReport;
import com.moviepick.domain.report.entity.ReportStatus;
import com.moviepick.domain.report.entity.ReportTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommunityReportRepository
        extends JpaRepository<CommunityReport, Long> {

    // 관리자 신고 목록: 상태별 조회 (정렬은 Pageable로 전달)
    Page<CommunityReport> findByStatus(ReportStatus status, Pageable pageable);

    // 동일 사용자의 중복 신고 확인
    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId,
            ReportTargetType targetType,
            Long targetId
    );

    // 신고 처리 시 비관적 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CommunityReport r WHERE r.id = :reportId")
    Optional<CommunityReport> findByIdForUpdate(
            @Param("reportId") Long reportId
    );

    // PENDING 상태의 신고만 처리
    // 이미 처리된 신고라면 업데이트된 행 수가 0
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE CommunityReport r
            SET r.status = :newStatus
            WHERE r.id = :reportId
              AND r.status = :pendingStatus
            """)
    int updateStatusIfPending(
            @Param("reportId") Long reportId,
            @Param("newStatus") ReportStatus newStatus,
            @Param("pendingStatus") ReportStatus pendingStatus
    );

    // 동일 게시글 또는 댓글의 대기 중인 신고를 일괄 처리
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE CommunityReport r
        SET r.status = :newStatus
        WHERE r.targetType = :targetType
          AND r.targetId = :targetId
          AND r.status = :pendingStatus
        """)
    int updateAllPendingByTarget(
            @Param("targetType") ReportTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("newStatus") ReportStatus newStatus,
            @Param("pendingStatus") ReportStatus pendingStatus
    );
}
