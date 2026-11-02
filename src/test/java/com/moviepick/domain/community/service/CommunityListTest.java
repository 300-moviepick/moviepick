package com.moviepick.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviepick.domain.community.dto.response.CommentListResponse;
import com.moviepick.domain.community.dto.response.PostListResponse;
import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.community.entity.CommentLike;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.entity.PostLike;
import com.moviepick.domain.community.repository.CommentLikeRepository;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.community.repository.PostLikeRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 목록은 페이지를 먼저 조회하고 좋아요 수·내 좋아요 여부를 IN 쿼리로 채운다. 결과가 이전 GROUP BY 방식과 같은지 확인 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, PostService.class, PostLikeService.class,
        CommentService.class, CommentLikeService.class})
class CommunityListTest {

    @Autowired
    private PostService postService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private CommentLikeRepository commentLikeRepository;

    private Member writer;
    private Member liker1;
    private Member liker2;

    @BeforeEach
    void setUp() {
        writer = saveMember("writer");
        liker1 = saveMember("liker1");
        liker2 = saveMember("liker2");
    }

    @AfterEach
    void tearDown() {
        commentLikeRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        postLikeRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.createLocal(nickname + "@test.com", "encoded", nickname));
    }

    @Test
    @DisplayName("최신순 목록은 최신 글부터 나오고, 좋아요 수는 글마다 정확하며 좋아요가 없으면 0이다")
    void latestPostsWithLikeCounts() {
        Post first = postRepository.save(Post.create(writer, "첫 글", "내용"));
        Post second = postRepository.save(Post.create(writer, "둘째 글", "내용"));
        Post third = postRepository.save(Post.create(writer, "셋째 영화 글", "내용"));
        postLikeRepository.save(PostLike.create(first, liker1));
        postLikeRepository.save(PostLike.create(first, liker2));
        postLikeRepository.save(PostLike.create(third, liker1));

        List<PostListResponse> posts = postService.getPosts(null, "latest", PageRequest.of(0, 20)).getContent();

        assertThat(posts).extracting(PostListResponse::postId)
                .containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(posts).extracting(PostListResponse::likeCount).containsExactly(1L, 0L, 2L);

        List<PostListResponse> searched = postService.getPosts("영화", "latest", PageRequest.of(0, 20)).getContent();
        assertThat(searched).extracting(PostListResponse::postId).containsExactly(third.getId());
        assertThat(searched).extracting(PostListResponse::likeCount).containsExactly(1L);
    }

    @Test
    @DisplayName("댓글 목록은 오래된 순이고, 좋아요 수와 내 좋아요 여부가 댓글마다 정확하다")
    void commentsWithLikeCountsAndLikedByMe() {
        Post post = postRepository.save(Post.create(writer, "글", "내용"));
        Comment older = commentRepository.save(Comment.create(post, writer, "먼저 쓴 댓글"));
        Comment newer = commentRepository.save(Comment.create(post, writer, "나중 쓴 댓글"));
        commentLikeRepository.save(CommentLike.create(older, liker1));
        commentLikeRepository.save(CommentLike.create(older, liker2));
        commentLikeRepository.save(CommentLike.create(newer, liker2));

        List<CommentListResponse> forLiker1 =
                commentService.getComments(post.getId(), liker1.getId(), PageRequest.of(0, 20)).getContent();

        assertThat(forLiker1).extracting(CommentListResponse::commentId)
                .containsExactly(older.getId(), newer.getId());
        assertThat(forLiker1).extracting(CommentListResponse::likeCount).containsExactly(2L, 1L);
        assertThat(forLiker1).extracting(CommentListResponse::likedByMe).containsExactly(true, false);

        List<CommentListResponse> forGuest =
                commentService.getComments(post.getId(), null, PageRequest.of(0, 20)).getContent();
        assertThat(forGuest).extracting(CommentListResponse::likedByMe).containsExactly(false, false);
    }
}
