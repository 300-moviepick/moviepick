
package com.moviepick.domain.report.service;

import com.moviepick.domain.report.dto.request.AdminReportProcessRequest;
import com.moviepick.domain.report.dto.response.AdminReportResponse;
import com.moviepick.domain.report.entity.AdminSanction;
import com.moviepick.domain.report.repository.AdminSanctionRepository;
import com.moviepick.domain.community.entity.*;
import com.moviepick.domain.report.entity.CommunityReport;
import com.moviepick.domain.report.entity.ReportStatus;
import com.moviepick.domain.report.entity.ReportTargetType;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.community.service.CommentService;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.domain.report.exception.ReportErrorCode;
import com.moviepick.global.exception.CommonErrorCode;
import com.moviepick.domain.member.entity.Role;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private final CommunityReportRepository communityReportRepository;
    private final AdminSanctionRepository adminSanctionRepository;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final CommentService commentService;
    private final EntityManager entityManager;

    // 관리자 신고 목록 정렬: 최신순 (같은 시각이면 id 내림차순)
    private static final Sort REPORT_LIST_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    // 관리자: 신고 목록 조회 (status가 null이면 전체)
    public Page<AdminReportResponse> getReports(ReportStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, REPORT_LIST_SORT);

        Page<CommunityReport> reports = status == null
                ? communityReportRepository.findAll(pageable)
                : communityReportRepository.findByStatus(status, pageable);

        // 신고 대상 id를 유형별로 모아 한 번에 조회 (행마다 조회하던 N+1 제거)
        Set<Long> postIds = new HashSet<>();
        Set<Long> reviewIds = new HashSet<>();
        for (CommunityReport report : reports.getContent()) {
            if (report.getTargetType() == ReportTargetType.POST) {
                postIds.add(report.getTargetId());
            } else if (report.getTargetType() == ReportTargetType.REVIEW) {
                reviewIds.add(report.getTargetId());
            }
        }

        Map<Long, Post> postMap = findPostMap(postIds);
        Map<Long, Review> reviewMap = findReviewMap(reviewIds);

        return reports.map(report -> createReportResponse(report, postMap, reviewMap));
    }

    // 신고 대상 게시글 일괄 조회 (id가 없으면 쿼리를 보내지 않는다)
    private Map<Long, Post> findPostMap(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return communityReportRepository.findPostsWithMemberByIdIn(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
    }

    // 신고 대상 리뷰 일괄 조회 (id가 없으면 쿼리를 보내지 않는다)
    private Map<Long, Review> findReviewMap(Collection<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return communityReportRepository.findReviewsWithMemberByIdIn(reviewIds).stream()
                .collect(Collectors.toMap(Review::getId, Function.identity()));
    }

    // 관리자: 신고 승인 또는 반려
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminReportResponse processReport(
            Long reportId,
            AdminReportProcessRequest request
    ) {
        // 1. 요청값 검증
        if (request == null || request.status() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        ReportStatus newStatus = request.status();

        if (newStatus != ReportStatus.ACCEPTED
                && newStatus != ReportStatus.REJECTED) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // 2. 신고 조회 (아직 잠금 획득하지 않음)
        CommunityReport initialReport = communityReportRepository
                .findById(reportId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.NOT_FOUND));

        // 3. 신고 대상 작성자 확인
        Long memberId = findReportedMemberId(initialReport);

        // 4. 회원 잠금 먼저 획득
        // 동일 작성자에 대한 신고 승인 작업을 직렬화
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.NOT_FOUND));

        // 5. 신고 행 잠금 획득
        CommunityReport report = communityReportRepository
                .findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.NOT_FOUND));

        // 6. 잠금 획득 후 신고 상태 재검증
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        // 7. 신고 반려
        if (newStatus == ReportStatus.REJECTED) {
            int updated = communityReportRepository.updateStatusIfPending(
                    reportId,
                    ReportStatus.REJECTED,
                    ReportStatus.PENDING
            );

            if (updated == 0) {
                throw new BusinessException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
            }

            entityManager.refresh(report);
            return createReportResponse(report);
        }

        // 8. 관리자 또는 탈퇴 회원 제재 제외
        boolean skipSanction =
                member.getRole() == Role.ADMIN
                        || member.getStatus() == MemberStatus.WITHDRAWN;

        // 9. 기존 제재 여부 확인
        boolean alreadySanctioned =
                adminSanctionRepository.existsByReportTarget(
                        report.getTargetType(),
                        report.getTargetId()
                );

        // 10. 게시글 신고 승인 시 블라인드 처리
        if (report.getTargetType() == ReportTargetType.POST) {
            Post post = postRepository.findById(report.getTargetId())
                    .orElseThrow(() ->
                            new BusinessException(CommonErrorCode.NOT_FOUND));

            // 작성자가 이미 삭제한 게시글은 DELETED 상태 유지
            if (post.getStatus() != PostStatus.DELETED) {
                post.blind();
            }
        }

        // 11. 신고 승인
        int updated = communityReportRepository.updateStatusIfPending(
                reportId,
                ReportStatus.ACCEPTED,
                ReportStatus.PENDING
        );

        if (updated == 0) {
            throw new BusinessException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        entityManager.refresh(report);

        // 12. 동일 대상의 나머지 PENDING 신고 승인
        communityReportRepository.updateAllPendingByTarget(
                report.getTargetType(),
                report.getTargetId(),
                ReportStatus.ACCEPTED,
                ReportStatus.PENDING
        );

        // 13. 중복 제재 방지
        if (!alreadySanctioned && !skipSanction) {
            long count =
                    adminSanctionRepository.countByMemberId(memberId) + 1;

            LocalDateTime suspendedUntil;

            if (count == 1) {
                suspendedUntil = LocalDateTime.now().plusDays(1);
            } else if (count == 2) {
                suspendedUntil = LocalDateTime.now().plusDays(7);
            } else {
                suspendedUntil = null;
            }

            // 영구 정지된 회원은 영구 정지 유지
            if (member.getStatus() == MemberStatus.SUSPENDED
                    && member.getSuspendedUntil() == null) {
                suspendedUntil = null;
            }

            member.suspend(suspendedUntil);

            AdminSanction sanction = AdminSanction.create(
                    member,
                    report,
                    Math.toIntExact(count),
                    suspendedUntil
            );

            adminSanctionRepository.save(sanction);
        }

        // 14. 댓글 신고 승인 시 숨김 + 게시글 댓글 수 감소 (관리자 PATCH …/blind와 같은 코드 경로, B1)
        // 공개 댓글만 숨기고 줄이며, 작성자가 이미 삭제했거나 이미 숨긴 댓글은 그대로 둔다.
        // 댓글 수 감소 쿼리가 실행 전에 변경을 반영하고 영속성 컨텍스트를 비우므로 회원 정지·제재 저장 뒤에 실행한다
        // (댓글 존재 여부는 3단계 findReportedMemberId에서 이미 확인)
        if (report.getTargetType() == ReportTargetType.COMMENT) {
            commentService.blindComment(report.getTargetId());
        }

        // 14-1. 리뷰 신고 승인 시 숨김 + 평점 차감 (관리자 PATCH …/blind와 같은 코드 경로, B3)
        // 공개 리뷰만 숨기고 차감하며, 이미 숨김·삭제된 리뷰는 그대로 둔다.
        // 이 처리도 상태 변경 쿼리가 영속성 컨텍스트를 비우므로 회원 정지·제재 저장 뒤에 실행한다
        if (report.getTargetType() == ReportTargetType.REVIEW) {
            reviewService.blind(report.getTargetId());
        }

        // 15. 처리 결과 반환
        return createReportResponse(report);
    }


    // 신고 대상 게시글·댓글·리뷰 작성자 찾기
    private Long findReportedMemberId(CommunityReport report) {

        switch (report.getTargetType()) {

            case POST -> {

                Post post = postRepository
                        .findById(report.getTargetId())
                        .orElseThrow(() -> new BusinessException(
                                CommonErrorCode.NOT_FOUND
                        ));

                return post.getMember().getId();
            }

            case COMMENT -> {

                Comment comment = commentRepository
                        .findById(report.getTargetId())
                        .orElseThrow(() -> new BusinessException(
                                CommonErrorCode.NOT_FOUND
                        ));

                return comment.getMember().getId();
            }

            case REVIEW -> {

                Review review = reviewRepository
                        .findById(report.getTargetId())
                        .orElseThrow(() -> new BusinessException(
                                CommonErrorCode.NOT_FOUND
                        ));

                return review.getMember().getId();
            }

            default -> throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }
    }
    // 게시글 본문·리뷰 내용을 최대 100자로 잘라 미리보기 생성
    private String createPreview(String content) {
        if (content == null) {
            return null;
        }

        if (content.length() <= 100) {
            return content;
        }

        return content.substring(0, 100);
    }

    // 신고 대상에 맞는 관리자 응답 생성
    private AdminReportResponse createReportResponse(CommunityReport report) {
        if (report.getTargetType() == ReportTargetType.POST) {
            return postRepository.findById(report.getTargetId())
                    .map(post -> AdminReportResponse.from(
                            report,
                            post.getTitle(),
                            createPreview(post.getContent()),
                            post.getMember().getNickname()
                    ))
                    .orElseGet(() -> AdminReportResponse.from(report));
        }

        if (report.getTargetType() == ReportTargetType.REVIEW) {
            return reviewRepository.findById(report.getTargetId())
                    .map(review -> AdminReportResponse.forReview(
                            report,
                            createPreview(review.getContent()),
                            review.getMember().getNickname(),
                            review.getMovie().getId()
                    ))
                    .orElseGet(() -> AdminReportResponse.from(report));
        }

        return AdminReportResponse.from(report);
    }

    // 신고 목록용 응답 생성: 미리 조회한 대상으로 만든다 (대상이 없으면 대상 정보 없이 반환)
    private AdminReportResponse createReportResponse(
            CommunityReport report,
            Map<Long, Post> postMap,
            Map<Long, Review> reviewMap
    ) {
        if (report.getTargetType() == ReportTargetType.POST) {
            Post post = postMap.get(report.getTargetId());
            if (post == null) {
                return AdminReportResponse.from(report);
            }
            return AdminReportResponse.from(
                    report,
                    post.getTitle(),
                    createPreview(post.getContent()),
                    post.getMember().getNickname()
            );
        }

        if (report.getTargetType() == ReportTargetType.REVIEW) {
            Review review = reviewMap.get(report.getTargetId());
            if (review == null) {
                return AdminReportResponse.from(report);
            }
            return AdminReportResponse.forReview(
                    report,
                    createPreview(review.getContent()),
                    review.getMember().getNickname(),
                    review.getMovie().getId()
            );
        }

        return AdminReportResponse.from(report);
    }
}
