package com.moviepick.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.report.dto.request.AdminReportProcessRequest;
import com.moviepick.domain.report.repository.AdminSanctionRepository;
import com.moviepick.domain.report.service.AdminReportService;
import com.moviepick.domain.community.dto.request.CommentCreateRequest;
import com.moviepick.domain.community.dto.request.CommentUpdateRequest;
import com.moviepick.domain.community.dto.response.CommentListResponse;
import com.moviepick.domain.report.entity.CommunityReport;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.report.entity.ReportStatus;
import com.moviepick.domain.report.entity.ReportTargetType;
import com.moviepick.domain.community.repository.CommentLikeRepository;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 댓글 신고 승인 → 숨김(BLINDED), 자리 유지 + 문구, 원문 미전송, 숨긴 댓글 조작 거절 (REPORT-02, B1) */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, AdminReportService.class,
        CommentService.class, CommentLikeService.class})
class CommentBlindTest {

    private static final AdminReportProcessRequest ACCEPT =
            new AdminReportProcessRequest(ReportStatus.ACCEPTED);
    private static final String BLINDED_TEXT = "관리자에 의해 가려진 댓글";

    @Autowired
    private AdminReportService adminReportService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private CommentLikeService commentLikeService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private CommentLikeRepository commentLikeRepository;
    @Autowired
    private CommunityReportRepository communityReportRepository;
    @Autowired
    private AdminSanctionRepository adminSanctionRepository;
    // 리뷰 신고 승인용 의존성. 이 테스트는 리뷰를 다루지 않는다
    @MockitoBean
    private ReviewService reviewService;

    private Member writer;
    private Member reporter;
    private Post post;
    private Long blindTargetId;
    private Long otherCommentId;

    @BeforeEach
    void setUp() {
        writer = saveMember("writer");
        reporter = saveMember("reporter");
        post = postRepository.save(Post.create(saveMember("poster"), "title", "content"));
        blindTargetId = commentService.createComment(writer.getId(), post.getId(),
                new CommentCreateRequest("신고될 원문")).commentId();
        otherCommentId = commentService.createComment(reporter.getId(), post.getId(),
                new CommentCreateRequest("다른 댓글")).commentId();
    }

    @AfterEach
    void tearDown() {
        adminSanctionRepository.deleteAllInBatch();
        communityReportRepository.deleteAllInBatch();
        commentLikeRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("댓글 신고 승인: 댓글 BLINDED, 글의 댓글 수 −1, 작성자 제재 1회")
    void acceptCommentReport() {
        acceptReport(blindTargetId);

        assertThat(statusOf(blindTargetId)).isEqualTo("BLINDED");
        assertThat(commentCount()).isEqualTo(1);
        assertThat(adminSanctionRepository.countByMemberId(writer.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("목록: 숨긴 댓글은 자리 유지, blinded=true, 내용은 안내 문구(원문 없음). 다른 댓글은 blinded=false")
    void listKeepsBlindedPlace() {
        acceptReport(blindTargetId);

        List<CommentListResponse> comments = commentService
                .getComments(post.getId(), null, PageRequest.of(0, 20)).getContent();

        assertThat(comments).hasSize(2);
        CommentListResponse blinded = find(comments, blindTargetId);
        CommentListResponse other = find(comments, otherCommentId);
        assertThat(blindedOf(blinded)).isEqualTo(Boolean.TRUE);
        assertThat(blinded.content()).isEqualTo(BLINDED_TEXT);
        assertThat(blindedOf(other)).isEqualTo(Boolean.FALSE);
        assertThat(other.content()).isEqualTo("다른 댓글");
    }

    @Test
    @DisplayName("숨긴 댓글은 작성자 본인도 수정·삭제할 수 없고, 다른 회원은 좋아요·좋아요 취소를 할 수 없다 (COMMENT_BLINDED)")
    void blindedCommentIsLocked() {
        Member liker = saveMember("liker");
        commentLikeService.addLike(liker.getId(), blindTargetId);   // 숨기기 전에 누른 좋아요
        acceptReport(blindTargetId);

        assertBlinded(() -> commentService.updateComment(writer.getId(), blindTargetId,
                new CommentUpdateRequest("고친 내용")));
        assertBlinded(() -> commentService.deleteCommentById(writer.getId(), blindTargetId));
        assertBlinded(() -> commentLikeService.addLike(reporter.getId(), blindTargetId));
        assertBlinded(() -> commentLikeService.removeLike(liker.getId(), blindTargetId));
        assertThat(statusOf(blindTargetId)).isEqualTo("BLINDED");
        assertThat(commentCount()).isEqualTo(1);
    }

    // 아래 관리자 숨김 테스트는 테스트 트랜잭션 없이(NOT_SUPPORTED) 서비스 호출이 커밋된 뒤,
    // statusOf·commentCount 등으로 새로 조회한 DB 값을 검증한다 (메모리의 엔티티가 아님)

    @Test
    @DisplayName("신고로 숨긴 댓글을 관리자가 다시 숨겨도 BLINDED 유지, 댓글 수는 다시 줄지 않는다")
    void adminBlindsReportBlindedComment() {
        acceptReport(blindTargetId);
        assertThat(commentCount()).isEqualTo(1);   // 숨길 때 이미 1 줄었다

        commentService.blindComment(blindTargetId);

        assertThat(statusOf(blindTargetId)).isEqualTo("BLINDED");
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 숨김: 공개 댓글은 BLINDED가 되고 글의 댓글 수가 1 준다")
    void adminBlindsVisibleComment() {
        assertThat(commentCount()).isEqualTo(2);

        commentService.blindComment(blindTargetId);

        assertThat(statusOf(blindTargetId)).isEqualTo("BLINDED");
        assertThat(statusOf(otherCommentId)).isEqualTo("VISIBLE");
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 숨김: 같은 댓글을 두 번 숨겨도 댓글 수는 한 번만 준다")
    void adminBlindTwiceDecreasesOnce() {
        commentService.blindComment(blindTargetId);
        commentService.blindComment(blindTargetId);

        assertThat(statusOf(blindTargetId)).isEqualTo("BLINDED");
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 숨김: 작성자가 이미 삭제한 댓글은 DELETED 유지, 댓글 수 변화 없음")
    void adminBlindDeletedCommentNoChange() {
        commentService.deleteCommentById(writer.getId(), blindTargetId);
        assertThat(commentCount()).isEqualTo(1);

        commentService.blindComment(blindTargetId);

        assertThat(statusOf(blindTargetId)).isEqualTo("DELETED");
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 숨김은 신고 상태와 회원 제재를 바꾸지 않는다 (신고 PENDING 유지, 제재 0건, 작성자 ACTIVE)")
    void adminBlindLeavesReportsAndSanctions() {
        Long reportId = saveReport(blindTargetId);

        commentService.blindComment(blindTargetId);

        assertThat(statusOf(blindTargetId)).isEqualTo("BLINDED");
        assertThat(communityReportRepository.findById(reportId).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.PENDING);
        assertThat(adminSanctionRepository.countByMemberId(writer.getId())).isZero();
        assertThat(memberRepository.findById(writer.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 삭제된 댓글의 신고를 승인하면 DELETED 유지, 댓글 수 변화 없음, 제재는 1회")
    void acceptReportOfDeletedComment() {
        Long reportId = saveReport(blindTargetId);
        commentService.deleteCommentById(writer.getId(), blindTargetId);

        adminReportService.processReport(reportId, ACCEPT);

        assertThat(statusOf(blindTargetId)).isEqualTo("DELETED");
        assertThat(commentCount()).isEqualTo(1);
        assertThat(adminSanctionRepository.countByMemberId(writer.getId())).isEqualTo(1);
    }

    private void acceptReport(Long commentId) {
        adminReportService.processReport(saveReport(commentId), ACCEPT);
    }

    private Long saveReport(Long commentId) {
        return communityReportRepository.save(CommunityReport.create(
                reporter, ReportTargetType.COMMENT, commentId, "ABUSE", null)).getId();
    }

    private String statusOf(Long commentId) {
        return commentRepository.findById(commentId).orElseThrow().getStatus().name();
    }

    private int commentCount() {
        return postRepository.findById(post.getId()).orElseThrow().getCommentCount();
    }

    private static CommentListResponse find(List<CommentListResponse> comments, Long commentId) {
        return comments.stream().filter(c -> c.commentId().equals(commentId)).findFirst().orElseThrow();
    }

    private void assertBlinded(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().toString())
                .isEqualTo("COMMENT_BLINDED");
    }

    // 응답 레코드의 blinded 값 (필드가 없으면 null)
    private static Object blindedOf(Object response) {
        RecordComponent component = Arrays.stream(response.getClass().getRecordComponents())
                .filter(c -> c.getName().equals("blinded"))
                .findFirst()
                .orElse(null);
        if (component == null) {
            return null;
        }
        try {
            return component.getAccessor().invoke(response);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.createLocal(nickname + "@test.com", "encoded", nickname));
    }
}
