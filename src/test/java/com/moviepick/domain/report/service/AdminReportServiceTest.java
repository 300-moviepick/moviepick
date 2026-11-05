package com.moviepick.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.report.dto.request.AdminReportProcessRequest;
import com.moviepick.domain.report.dto.response.AdminReportResponse;
import com.moviepick.domain.report.repository.AdminSanctionRepository;
import com.moviepick.domain.report.entity.CommunityReport;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.entity.PostStatus;
import com.moviepick.domain.report.entity.ReportStatus;
import com.moviepick.domain.report.entity.ReportTargetType;
import com.moviepick.domain.community.exception.PostErrorCode;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.community.service.CommentLikeService;
import com.moviepick.domain.community.service.CommentService;
import com.moviepick.domain.community.service.PostLikeService;
import com.moviepick.domain.community.service.PostService;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.entity.Role;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 벌크 UPDATE와 refresh가 실제 커밋 기준으로 동작하는지 보려고 테스트 트랜잭션을 끄고 H2로 확인한다. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, AdminReportService.class, PostService.class, PostLikeService.class,
        CommentService.class, CommentLikeService.class})
class AdminReportServiceTest {

    private static final AdminReportProcessRequest ACCEPT =
            new AdminReportProcessRequest(ReportStatus.ACCEPTED);

    @Autowired
    private AdminReportService adminReportService;
    // 관리자 게시글 수동 숨김은 게시글 서비스가 처리한다
    @Autowired
    private PostService postService;
    // 리뷰 신고 승인용 의존성. 이 테스트는 리뷰를 다루지 않는다
    @MockitoBean
    private ReviewService reviewService;
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
    @DisplayName("게시글 신고를 승인하면 글이 숨겨지고, 같은 글의 대기 신고가 모두 처리되며 제재는 1건이다")
    void acceptPostReport() {
        Member writer = saveMember("writer");
        Post post = postRepository.save(Post.create(writer, "title", "content"));
        Long reportId = saveReport(saveMember("reporter1"), post).getId();
        saveReport(saveMember("reporter2"), post);
        saveReport(saveMember("reporter3"), post);

        adminReportService.processReport(reportId, ACCEPT);

        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.BLINDED);
        assertThat(postRepository.findByIdAndStatus(post.getId(), PostStatus.VISIBLE)).isEmpty();
        assertThat(communityReportRepository.findAll())
                .allMatch(report -> report.getStatus() == ReportStatus.ACCEPTED);
        assertThat(adminSanctionRepository.count()).isEqualTo(1);

        Member suspended = memberRepository.findById(writer.getId()).orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(suspended.getSuspendedUntil()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴 회원의 글 신고를 승인하면 403 없이 글만 숨기고 제재는 하지 않는다")
    void acceptReportOfWithdrawnWriter() {
        Member writer = saveMember("writer");
        Post post = postRepository.save(Post.create(writer, "title", "content"));
        writer.withdraw();
        memberRepository.save(writer);
        Long reportId = saveReport(saveMember("reporter"), post).getId();

        AdminReportResponse response = adminReportService.processReport(reportId, ACCEPT);

        assertThat(response.status()).isEqualTo(ReportStatus.ACCEPTED);
        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.BLINDED);
        assertThat(adminSanctionRepository.count()).isZero();
        assertThat(memberRepository.findById(writer.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("관리자의 글 신고를 승인하면 글만 숨기고 관리자는 정지하지 않는다")
    void acceptReportOfAdminWriter() {
        Member admin = Member.createLocal("admin@test.com", "encoded", "admin");
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        memberRepository.save(admin);
        Post post = postRepository.save(Post.create(admin, "title", "content"));
        Long reportId = saveReport(saveMember("reporter"), post).getId();

        adminReportService.processReport(reportId, ACCEPT);

        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.BLINDED);
        assertThat(adminSanctionRepository.count()).isZero();
        assertThat(memberRepository.findById(admin.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("작성자가 삭제한 글의 신고를 승인하면 글은 DELETED로 남고, 신고 승인과 제재는 진행된다")
    void acceptReportOfDeletedPost() {
        Member writer = saveMember("writer");
        Post post = postRepository.save(Post.create(writer, "title", "content"));
        Long reportId = saveReport(saveMember("reporter1"), post).getId();
        saveReport(saveMember("reporter2"), post);
        post.delete();
        postRepository.save(post);

        AdminReportResponse response = adminReportService.processReport(reportId, ACCEPT);

        assertThat(response.status()).isEqualTo(ReportStatus.ACCEPTED);
        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.DELETED);
        assertThat(communityReportRepository.findAll())
                .allMatch(report -> report.getStatus() == ReportStatus.ACCEPTED);
        assertThat(adminSanctionRepository.count()).isEqualTo(1);
        assertThat(memberRepository.findById(writer.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.SUSPENDED);
    }

    @Test
    @DisplayName("삭제되지 않은 글의 신고를 승인하면 글은 BLINDED가 된다")
    void acceptReportOfVisiblePost() {
        Member writer = saveMember("writer");
        Post post = postRepository.save(Post.create(writer, "title", "content"));
        Long reportId = saveReport(saveMember("reporter"), post).getId();

        adminReportService.processReport(reportId, ACCEPT);

        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.BLINDED);
    }

    @Test
    @DisplayName("신고 목록에 대상 글 제목, 100자 미리보기, 작성자 닉네임이 담긴다")
    void getReportsWithPostInfo() {
        Member writer = saveMember("writer");
        Post post = postRepository.save(Post.create(writer, "title", "a".repeat(150)));
        saveReport(saveMember("reporter"), post);

        AdminReportResponse response = adminReportService.getReports(null, 0, 20).getContent().get(0);

        assertThat(response.postTitle()).isEqualTo("title");
        assertThat(response.postContentPreview()).hasSize(100);
        assertThat(response.authorNickname()).isEqualTo("writer");
    }

    @Test
    @DisplayName("신고 목록 status 필터: PENDING·ACCEPTED·REJECTED는 해당 상태만, 생략하면 전체")
    void getReportsByStatus() {
        Post post = postRepository.save(Post.create(saveMember("writer"), "title", "content"));
        Long pending = saveReport(saveMember("reporter1"), post, ReportStatus.PENDING).getId();
        Long accepted = saveReport(saveMember("reporter2"), post, ReportStatus.ACCEPTED).getId();
        Long rejected = saveReport(saveMember("reporter3"), post, ReportStatus.REJECTED).getId();

        assertThat(reportIds(ReportStatus.PENDING)).containsExactly(pending);
        assertThat(reportIds(ReportStatus.ACCEPTED)).containsExactly(accepted);
        assertThat(reportIds(ReportStatus.REJECTED)).containsExactly(rejected);
        assertThat(reportIds(null)).containsExactlyInAnyOrder(pending, accepted, rejected);
    }

    @Test
    @DisplayName("신고 목록은 최신순(createdAt 내림차순)이다")
    void getReportsLatestFirst() {
        Post post = postRepository.save(Post.create(saveMember("writer"), "title", "content"));
        Long first = saveReport(saveMember("reporter1"), post).getId();
        Long second = saveReport(saveMember("reporter2"), post).getId();
        Long third = saveReport(saveMember("reporter3"), post).getId();

        assertThat(reportIds(null)).containsExactly(third, second, first);
        assertThat(reportIds(ReportStatus.PENDING)).containsExactly(third, second, first);
    }

    @Test
    @DisplayName("관리자 수동 숨김은 글을 BLINDED로 바꾸고, 삭제된 글은 404다")
    void blindPost() {
        Member writer = saveMember("writer");
        Post post = postRepository.save(Post.create(writer, "title", "content"));
        Post deleted = Post.create(writer, "deleted", "content");
        deleted.delete();
        postRepository.save(deleted);

        postService.blindByAdmin(post.getId());

        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.BLINDED);
        assertThatThrownBy(() -> postService.blindByAdmin(deleted.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PostErrorCode.POST_NOT_FOUND);
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(
                Member.createLocal(nickname + "@test.com", "encoded", nickname));
    }

    private CommunityReport saveReport(Member reporter, Post post) {
        return communityReportRepository.save(
                CommunityReport.create(reporter, ReportTargetType.POST, post.getId(), "SPAM", null));
    }

    // 목록 필터 확인용: 처리 상태를 정해서 저장
    private CommunityReport saveReport(Member reporter, Post post, ReportStatus status) {
        CommunityReport report =
                CommunityReport.create(reporter, ReportTargetType.POST, post.getId(), "SPAM", null);
        ReflectionTestUtils.setField(report, "status", status);
        return communityReportRepository.save(report);
    }

    private java.util.List<Long> reportIds(ReportStatus status) {
        return adminReportService.getReports(status, 0, 20).getContent().stream()
                .map(AdminReportResponse::reportId)
                .toList();
    }
}
