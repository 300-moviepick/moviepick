package com.moviepick.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.community.service.CommentService;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.domain.report.dto.response.AdminReportResponse;
import com.moviepick.domain.report.entity.CommunityReport;
import com.moviepick.domain.report.entity.ReportTargetType;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 신고 목록: 대상(게시글·리뷰)과 작성자를 유형별 IN 조회로 가져와 쿼리 수가 size와 무관하게 4개 이하인지 확인한다. */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, AdminReportService.class})
class AdminReportListQueryTest {

    // 목록에 없는 게시글 id (대상이 삭제된 신고)
    private static final long MISSING_POST_ID = 999_999L;

    @Autowired
    private AdminReportService adminReportService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private CommunityReportRepository communityReportRepository;
    // 신고 처리용 의존성. 이 테스트는 목록 조회만 다룬다
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private CommentService commentService;

    @AfterEach
    void tearDown() {
        communityReportRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("신고 목록은 대상 유형이 섞여 있어도 쿼리 4개 이하로 대상 정보를 채우고, 대상 없는 신고도 포함한다")
    void getReportsWithinFourQueries() {
        Member reporter = saveMember("reporter");
        Movie movie = movieRepository.save(new Movie(1L, "movie"));
        List<Long> savedReportIds = new ArrayList<>();

        // 게시글 신고 3개 (작성자가 모두 다름)
        List<Post> posts = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Post post = postRepository.save(
                    Post.create(saveMember("postWriter" + i), "title" + i, "content" + i + "a".repeat(150)));
            posts.add(post);
            savedReportIds.add(saveReport(reporter, ReportTargetType.POST, post.getId()));
        }

        // 리뷰 신고 3개 (작성자가 모두 다름)
        List<Review> reviews = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Review review = reviewRepository.save(Review.create(
                    saveMember("reviewWriter" + i), movie, new BigDecimal("4.0"), "review" + i, false));
            reviews.add(review);
            savedReportIds.add(saveReport(reporter, ReportTargetType.REVIEW, review.getId()));
        }

        // 댓글 신고 1개 (목록에서는 대상 조회를 하지 않는다)
        Comment comment = commentRepository.save(
                Comment.create(posts.get(0), saveMember("commentWriter"), "comment"));
        Long commentReportId = saveReport(reporter, ReportTargetType.COMMENT, comment.getId());
        savedReportIds.add(commentReportId);

        // 대상이 없는 게시글 신고 1개
        Long missingReportId = saveReport(reporter, ReportTargetType.POST, MISSING_POST_ID);
        savedReportIds.add(missingReportId);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<AdminReportResponse> content = adminReportService.getReports(null, 0, 20).getContent();

        // 쿼리 수: 신고 페이지 + (count) + 게시글 IN + 리뷰 IN
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);

        // 정렬: createdAt DESC, id DESC (저장 역순)
        List<Long> expectedOrder = new ArrayList<>(savedReportIds);
        Collections.reverse(expectedOrder);
        assertThat(content).extracting(AdminReportResponse::reportId).containsExactlyElementsOf(expectedOrder);

        Map<Long, AdminReportResponse> byReportId = content.stream()
                .collect(Collectors.toMap(AdminReportResponse::reportId, Function.identity()));

        // 게시글 신고: 제목, 100자 미리보기, 작성자 닉네임
        for (int i = 0; i < 3; i++) {
            AdminReportResponse response = byReportId.get(savedReportIds.get(i));
            Post post = posts.get(i);
            assertThat(response.targetType()).isEqualTo(ReportTargetType.POST);
            assertThat(response.postTitle()).isEqualTo(post.getTitle());
            assertThat(response.postContentPreview()).isEqualTo(post.getContent().substring(0, 100));
            assertThat(response.authorNickname()).isEqualTo("postWriter" + (i + 1));
            assertThat(response.reviewContentPreview()).isNull();
            assertThat(response.movieId()).isNull();
        }

        // 리뷰 신고: 리뷰 내용 미리보기, 작성자 닉네임, 영화 번호
        for (int i = 0; i < 3; i++) {
            AdminReportResponse response = byReportId.get(savedReportIds.get(3 + i));
            assertThat(response.targetType()).isEqualTo(ReportTargetType.REVIEW);
            assertThat(response.reviewContentPreview()).isEqualTo(reviews.get(i).getContent());
            assertThat(response.authorNickname()).isEqualTo("reviewWriter" + (i + 1));
            assertThat(response.movieId()).isEqualTo(movie.getId());
            assertThat(response.postTitle()).isNull();
        }

        // 댓글 신고와 대상 없는 신고: 대상 정보 없이 목록에 나온다
        for (Long reportId : List.of(commentReportId, missingReportId)) {
            AdminReportResponse response = byReportId.get(reportId);
            assertThat(response.reporterId()).isEqualTo(reporter.getId());
            assertThat(response.postTitle()).isNull();
            assertThat(response.postContentPreview()).isNull();
            assertThat(response.authorNickname()).isNull();
            assertThat(response.reviewContentPreview()).isNull();
            assertThat(response.movieId()).isNull();
        }
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(
                Member.createLocal(nickname + "@test.com", "encoded", nickname));
    }

    private Long saveReport(Member reporter, ReportTargetType targetType, Long targetId) {
        return communityReportRepository.save(
                CommunityReport.create(reporter, targetType, targetId, "SPAM", null)).getId();
    }
}
