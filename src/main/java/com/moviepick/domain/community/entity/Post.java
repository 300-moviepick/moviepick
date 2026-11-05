
package com.moviepick.domain.community.entity;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(
        name = "posts",
        // 게시글 최신순 목록
        indexes = @Index(name = "idx_post_status_created_at", columnList = "status, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DynamicUpdate
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int viewCount = 0;

    @Column(nullable = false)
    private int commentCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.VISIBLE;

    // 게시글 생성
    public static Post create(Member member, String title, String content) {
        if (member == null) {
            throw new IllegalArgumentException("게시글 작성자는 필수입니다.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("게시글 제목은 필수입니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("게시글 내용은 필수입니다.");
        }

        Post post = new Post();
        post.member = member;
        post.title = title;
        post.content = content;
        post.viewCount = 0;
        post.commentCount = 0;
        post.status = PostStatus.VISIBLE;

        return post;
    }

    // 게시글 수정
    public void update(String title, String content) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("게시글 제목은 필수입니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("게시글 내용은 필수입니다.");
        }

        this.title = title;
        this.content = content;
    }

    // 게시글 논리 삭제
    public void delete() {
        this.status = PostStatus.DELETED;
    }

    // 관리자: 신고 승인 또는 수동 조치로 게시글 숨김
    public void blind() {
        this.status = PostStatus.BLINDED;
    }
}
