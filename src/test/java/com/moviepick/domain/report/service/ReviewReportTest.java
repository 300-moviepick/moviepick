package com.moviepick.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.report.dto.request.AdminReportProcessRequest;
import com.moviepick.domain.report.dto.response.AdminReportResponse;
import com.moviepick.domain.report.repository.AdminSanctionRepository;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.domain.movie.service.MovieRatingService;
import com.moviepick.domain.review.dto.request.ReviewCreateRequest;
import com.moviepick.domain.review.repository.ReviewLikeRepository;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.domain.report.entity.ReportStatus;
import com.moviepick.global.security.RefreshTokenService;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 리뷰 신고: 작성·거절 조건, 승인 → 리뷰 숨김 + 평점 1회 차감 + 제재, 관리자 목록 대상 정보 (REVIEW-05, B3) */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, CommunityReportService.class, AdminReportService.class,
        ReviewService.class, MovieRatingService.class, MemberService.class})
class ReviewReportTest {

    private static final AdminReportProcessRequest ACCEPT =
            new AdminReportProcessRequest(ReportStatus.ACCEPTED);
    private static final AdminReportProcessRequest REJECT =
            new AdminReportProcessRequest(ReportStatus.REJECTED);

    @Autowired
    private CommunityReportService communityReportService;
    @Autowired
    private AdminReportService adminReportService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ReviewLikeRepository reviewLikeRepository;
    @Autowired
    private CommunityReportRepository communityReportRepository;
    @Autowired
    private AdminSanctionRepository adminSanctionRepository;
    @MockitoBean
    private RefreshTokenService refreshTokenService;

    private Member writer;
    private Member reporter;
    private Member reporter2;
    private Long movieId;
    private Long reviewId;

    @BeforeEach
    void setUp() {
        writer = saveMember("writer");
        reporter = saveMember("reporter");
        reporter2 = saveMember("reporter2");
        movieId = movieRepository.save(new Movie(1L, "movie")).getId();
        reviewId = reviewService.createReview(writer.getId(), movieId,
                new ReviewCreateRequest(new BigDecimal("4.0"), "신고될 리뷰 내용", false)).reviewId();
    }

    @AfterEach
    void tearDown() {
        adminSanctionRepository.deleteAllInBatch();
        communityReportRepository.deleteAllInBatch();
        reviewLikeRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("리뷰 신고: 대상 종류 REVIEW로 대기 상태 저장")
    void reportReview() {
        Long reportId = report(reporter, reviewId);

        var saved = communityReportRepository.findById(reportId).orElseThrow();
        assertThat(saved.getTargetType().name()).isEqualTo("REVIEW");
        assertThat(saved.getTargetId()).isEqualTo(reviewId);
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("본인 리뷰 신고는 게시글과 같은 CANNOT_REPORT_OWN_CONTENT")
    void ownReview() {
        assertError(() -> report(writer, reviewId), "CANNOT_REPORT_OWN_CONTENT");
    }

    @Test
    @DisplayName("없는 리뷰·삭제된 리뷰·숨긴 리뷰는 REVIEW_NOT_FOUND")
    void notReportableReviews() {
        assertError(() -> report(reporter, 999_999L), "REVIEW_NOT_FOUND");

        reviewService.blind(reviewId);
        assertError(() -> report(reporter, reviewId), "REVIEW_NOT_FOUND");

        Long otherReviewId = writeReview(reporter2, 2L);
        reviewService.deleteReview(reporter2.getId(), otherReviewId);
        assertError(() -> report(reporter, otherReviewId), "REVIEW_NOT_FOUND");
    }

    @Test
    @DisplayName("같은 리뷰 중복 신고는 409 DUPLICATE_REPORT")
    void duplicateReport() {
        report(reporter, reviewId);

        assertError(() -> report(reporter, reviewId), "DUPLICATE_REPORT");
    }

    @Test
    @DisplayName("비활성 영화의 리뷰도 신고할 수 있다 (D5와 같음)")
    void inactiveMovieReview() {
        Movie movie = movieRepository.findById(movieId).orElseThrow();
        ReflectionTestUtils.setField(movie, "active", false);
        movieRepository.save(movie);

        assertThat(report(reporter, reviewId)).isNotNull();
    }

    @Test
    @DisplayName("승인: 리뷰 BLINDED, 평점 1회 차감, 작성자 제재 1회, 같은 리뷰 대기 신고 일괄 승인")
    void acceptReviewReport() {
        Long reportId = report(reporter, reviewId);
        Long otherReportId = report(reporter2, reviewId);

        adminReportService.processReport(reportId, ACCEPT);

        assertThat(reviewStatus()).isEqualTo("BLINDED");
        assertRating("0.0", 0);
        assertThat(adminSanctionRepository.countByMemberId(writer.getId())).isEqualTo(1);
        assertThat(reportStatus(otherReportId)).isEqualTo(ReportStatus.ACCEPTED);
        assertThat(memberRepository.findById(writer.getId()).orElseThrow().getStatus().name())
                .isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("이미 숨긴 리뷰의 신고를 승인하면 평점을 다시 빼지 않고 제재는 1회")
    void acceptReportOfBlindedReview() {
        Long reportId = report(reporter, reviewId);
        reviewService.blind(reviewId);   // 관리자가 먼저 숨김 (평점 1회 차감)

        adminReportService.processReport(reportId, ACCEPT);

        assertThat(reviewStatus()).isEqualTo("BLINDED");
        assertRating("0.0", 0);
        assertThat(adminSanctionRepository.countByMemberId(writer.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 삭제된 리뷰의 신고를 승인하면 DELETED 유지, 평점 변화 없음, 제재는 1회")
    void acceptReportOfDeletedReview() {
        Long reportId = report(reporter, reviewId);
        reviewService.deleteReview(writer.getId(), reviewId);

        adminReportService.processReport(reportId, ACCEPT);

        assertThat(reviewStatus()).isEqualTo("DELETED");
        assertRating("0.0", 0);
        assertThat(adminSanctionRepository.countByMemberId(writer.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("반려: REJECTED, 리뷰와 평점은 그대로, 제재 없음")
    void rejectReviewReport() {
        Long reportId = report(reporter, reviewId);

        adminReportService.processReport(reportId, REJECT);

        assertThat(reportStatus(reportId)).isEqualTo(ReportStatus.REJECTED);
        assertThat(reviewStatus()).isEqualTo("VISIBLE");
        assertRating("4.0", 1);
        assertThat(adminSanctionRepository.countByMemberId(writer.getId())).isZero();
    }

    @Test
    @DisplayName("관리자 신고 목록: targetType REVIEW와 대상 정보(리뷰 내용 미리보기, 작성자 닉네임, 영화 번호)")
    void adminListShowsReviewTarget() {
        Long reportId = report(reporter, reviewId);

        AdminReportResponse item = adminReportService.getReports(0, 20).getContent().stream()
                .filter(r -> r.reportId().equals(reportId))
                .findFirst()
                .orElseThrow();

        assertThat(item.targetType().name()).isEqualTo("REVIEW");
        assertThat(item.authorNickname()).isEqualTo("writer");
        assertThat(component(item, "reviewContentPreview")).isEqualTo("신고될 리뷰 내용");
        assertThat(component(item, "movieId")).isEqualTo(movieId);
    }

    private Long report(Member member, Long targetReviewId) {
        return communityReportService.reportReview(member.getId(), targetReviewId, "SPOILER", null).reportId();
    }

    private Long writeReview(Member member, Long tmdbId) {
        Long otherMovieId = movieRepository.save(new Movie(tmdbId, "movie" + tmdbId)).getId();
        return reviewService.createReview(member.getId(), otherMovieId,
                new ReviewCreateRequest(new BigDecimal("3.0"), "다른 리뷰", false)).reviewId();
    }

    private String reviewStatus() {
        return reviewRepository.findById(reviewId).orElseThrow().getStatus().name();
    }

    private ReportStatus reportStatus(Long reportId) {
        return communityReportRepository.findById(reportId).orElseThrow().getStatus();
    }

    private void assertRating(String sum, int count) {
        Movie movie = movieRepository.findById(movieId).orElseThrow();
        assertThat(movie.getRatingSum()).isEqualByComparingTo(sum);
        assertThat(movie.getReviewCount()).isEqualTo(count);
    }

    private void assertError(Runnable call, String errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().toString())
                .isEqualTo(errorCode);
    }

    // 응답 레코드의 필드 값 (필드가 없으면 "missing")
    private static Object component(Object record, String name) {
        RecordComponent component = Arrays.stream(record.getClass().getRecordComponents())
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (component == null) {
            return "missing";
        }
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.createLocal(nickname + "@test.com", "encoded", nickname));
    }
}
