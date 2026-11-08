package com.moviepick.domain.community.entity;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "comment_likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_comment_like_member",
                        columnNames = {"comment_id", "member_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private CommentLike(Comment comment, Member member) {
        this.comment = comment;
        this.member = member;
    }

    public static CommentLike create(Comment comment, Member member) {
        if (comment == null) {
            throw new IllegalArgumentException("좋아요 대상 댓글은 필수입니다.");
        }

        if (member == null) {
            throw new IllegalArgumentException("좋아요를 누른 회원은 필수입니다.");
        }

        if (comment.getStatus() != CommentStatus.VISIBLE) {
            throw new IllegalArgumentException("삭제된 댓글에는 좋아요를 누를 수 없습니다.");
        }

        if (comment.getPost().getStatus() != PostStatus.VISIBLE) {
            throw new IllegalArgumentException("삭제된 게시글의 댓글에는 좋아요를 누를 수 없습니다.");
        }

        return new CommentLike(comment, member);
    }
}
