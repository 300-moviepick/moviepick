package com.moviepick.domain.report.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.report.dto.request.AdminReportProcessRequest;
import com.moviepick.domain.report.repository.AdminSanctionRepository;
import com.moviepick.domain.report.entity.CommunityReport;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.report.entity.ReportStatus;
import com.moviepick.domain.report.entity.ReportTargetType;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.community.service.CommentService;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.domain.report.exception.ReportErrorCode;
import com.moviepick.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** REPORT-04: 이미 처리된 신고를 다시 처리하면 신고자 중복과 다른 409 REPORT_ALREADY_PROCESSED */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, AdminReportService.class})
class AdminReportReprocessTest {

    private static final AdminReportProcessRequest ACCEPT =
            new AdminReportProcessRequest(ReportStatus.ACCEPTED);
    private static final AdminReportProcessRequest REJECT =
            new AdminReportProcessRequest(ReportStatus.REJECTED);

    @Autowired
    private AdminReportService adminReportService;
    // 리뷰 신고 승인용 의존성. 이 테스트는 리뷰를 다루지 않는다
    @MockitoBean
    private ReviewService reviewService;
    // 댓글 신고 승인용 의존성. 이 테스트는 댓글을 다루지 않는다
    @MockitoBean
    private CommentService commentService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CommunityReportRepository communityReportRepository;
    @Autowired
    private AdminSanctionRepository adminSanctionRepository;

    @AfterEach
    void tearDown() {
        adminSanctionRepository.deleteAllInBatch();
        communityReportRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("승인된 신고 재승인 → 409 REPORT_ALREADY_PROCESSED")
    void reacceptAccepted() {
        Long reportId = saveReport("a").getId();
        adminReportService.processReport(reportId, ACCEPT);

        assertAlreadyProcessed(reportId, ACCEPT);
    }

    @Test
    @DisplayName("같은 글 일괄 승인된 신고 반려 → 409 REPORT_ALREADY_PROCESSED")
    void rejectBulkAccepted() {
        Member writer = saveMember("writer");
        Post post = postRepository.save(Post.create(writer, "title", "content"));
        Long first = saveReport(saveMember("reporter1"), post).getId();
        Long second = saveReport(saveMember("reporter2"), post).getId();
        adminReportService.processReport(first, ACCEPT);

        assertAlreadyProcessed(second, REJECT);
    }

    @Test
    @DisplayName("반려된 신고 재반려 → 409 REPORT_ALREADY_PROCESSED")
    void rejectRejected() {
        Long reportId = saveReport("b").getId();
        adminReportService.processReport(reportId, REJECT);

        assertAlreadyProcessed(reportId, REJECT);
    }

    @Test
    @DisplayName("REPORT_ALREADY_PROCESSED는 409이고 신고자 중복(DUPLICATE_REPORT)과 문구가 다르다")
    void errorCodeShape() {
        ErrorCode code = ReportErrorCode.valueOf("REPORT_ALREADY_PROCESSED");

        org.assertj.core.api.Assertions.assertThat(code.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        org.assertj.core.api.Assertions.assertThat(code.getMessage())
                .isNotEqualTo(ReportErrorCode.DUPLICATE_REPORT.getMessage());
    }

    private void assertAlreadyProcessed(Long reportId, AdminReportProcessRequest request) {
        assertThatThrownBy(() -> adminReportService.processReport(reportId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().toString())
                .isEqualTo("REPORT_ALREADY_PROCESSED");
    }

    private CommunityReport saveReport(String prefix) {
        Member writer = saveMember(prefix + "writer");
        Post post = postRepository.save(Post.create(writer, "title", "content"));
        return saveReport(saveMember(prefix + "reporter"), post);
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(
                Member.createLocal(nickname + "@test.com", "encoded", nickname));
    }

    private CommunityReport saveReport(Member reporter, Post post) {
        return communityReportRepository.save(
                CommunityReport.create(reporter, ReportTargetType.POST, post.getId(), "SPAM", null));
    }
}
