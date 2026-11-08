package com.moviepick.domain.report.service;

import com.moviepick.domain.report.dto.response.CommunityReportResponse;
import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.community.entity.CommentStatus;
import com.moviepick.domain.report.entity.CommunityReport;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.entity.PostStatus;
import com.moviepick.domain.report.entity.ReportTargetType;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.exception.ReviewErrorCode;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.domain.report.exception.ReportErrorCode;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityReportService {

    private static final String REPORT_UNIQUE_CONSTRAINT =
            "uk_report_reporter_target";

    private final CommunityReportRepository communityReportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final ReviewRepository reviewRepository;

    // 게시글 신고
    @Transactional
    public CommunityReportResponse reportPost(
            Long reporterId,
            Long postId,
            String reason,
            String detail
    ) {
        Post post = postRepository
                .findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.NOT_FOUND));

        // 본인이 작성한 게시글은 신고할 수 없음
        if (post.getMember().getId().equals(reporterId)) {
            throw new BusinessException(
                    ReportErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }

        return createReport(
                reporterId,
                ReportTargetType.POST,
                post.getId(),
                reason,
                detail
        );
    }

    // 댓글 신고
    @Transactional
    public CommunityReportResponse reportComment(
            Long reporterId,
            Long commentId,
            String reason,
            String detail
    ) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.NOT_FOUND));

        if (comment.getStatus() != CommentStatus.VISIBLE
                || comment.getPost().getStatus() != PostStatus.VISIBLE) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        // 본인이 작성한 댓글은 신고할 수 없음
        if (comment.getMember().getId().equals(reporterId)) {
            throw new BusinessException(
                    ReportErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }

        return createReport(
                reporterId,
                ReportTargetType.COMMENT,
                comment.getId(),
                reason,
                detail
        );
    }

    // 리뷰 신고 (B3). 비활성 영화의 리뷰도 신고할 수 있다 (D5 리뷰 좋아요와 같음)
    @Transactional
    public CommunityReportResponse reportReview(
            Long reporterId,
            Long reviewId,
            String reason,
            String detail
    ) {
        // 없는 리뷰, 삭제·숨긴 리뷰는 신고할 수 없음
        Review review = reviewRepository.findById(reviewId)
                .filter(Review::isVisible)
                .orElseThrow(() ->
                        new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

        // 본인이 작성한 리뷰는 신고할 수 없음
        if (review.isOwnedBy(reporterId)) {
            throw new BusinessException(
                    ReportErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }

        return createReport(
                reporterId,
                ReportTargetType.REVIEW,
                review.getId(),
                reason,
                detail
        );
    }

    // 신고 공통 처리
    private CommunityReportResponse createReport(
            Long reporterId,
            ReportTargetType targetType,
            Long targetId,
            String reason,
            String detail
    ) {
        Member reporter = memberRepository.findById(reporterId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.UNAUTHORIZED));

        if (communityReportRepository
                .existsByReporterIdAndTargetTypeAndTargetId(
                        reporterId,
                        targetType,
                        targetId
                )) {
            throw new BusinessException(
                    ReportErrorCode.DUPLICATE_REPORT);
        }

        CommunityReport report;

        try {
            report = CommunityReport.create(
                    reporter,
                    targetType,
                    targetId,
                    reason,
                    detail
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        CommunityReport savedReport;

        try {
            // 동시 중복 신고는 사전 확인을 통과하므로 유니크 제약 위반을 이 메서드 안에서 확인한다.
            savedReport = communityReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            if (isReportUniqueConstraintViolation(exception)) {
                throw new BusinessException(ReportErrorCode.DUPLICATE_REPORT);
            }

            throw exception;
        }

        return CommunityReportResponse.from(savedReport);
    }

    // 같은 대상 중복 신고 유니크 제약 위반인지 확인
    private boolean isReportUniqueConstraintViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintException) {
                // MySQL은 "community_reports.uk_report_reporter_target", H2는 "PUBLIC.UK_REPORT_REPORTER_TARGET INDEX ..."로 알려 준다
                String name = constraintException.getConstraintName();
                return name != null
                        && name.toLowerCase().contains(REPORT_UNIQUE_CONSTRAINT);
            }

            cause = cause.getCause();
        }

        return false;
    }
}
