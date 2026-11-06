package com.moviepick.domain.ai.recommendation.entity;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.movie.entity.Movie;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "ai_recommendation_feedback",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_feedback_member_movie",
                columnNames = {"member_id", "movie_id"}
        ),
        indexes = @Index(
                name = "idx_ai_feedback_member_preference",
                columnList = "member_id, preference"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRecommendationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiFeedbackPreference preference;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    // 최초 피드백 생성
    public static AiRecommendationFeedback create(
            Member member,
            Movie movie,
            AiFeedbackPreference preference,
            String reason
    ) {
        if (member == null || movie == null) {
            throw new IllegalArgumentException(
                    "회원과 영화 정보가 필요합니다."
            );
        }

        AiRecommendationFeedback feedback =
                new AiRecommendationFeedback();

        feedback.member = member;
        feedback.movie = movie;
        feedback.change(preference, reason);

        return feedback;
    }

    // 기존 피드백 수정
    public void change(
            AiFeedbackPreference preference,
            String reason
    ) {
        if (preference == null) {
            throw new IllegalArgumentException(
                    "피드백 종류가 필요합니다."
            );
        }

        String normalizedReason =
                reason == null ? null : reason.trim();

        if (normalizedReason != null
                && normalizedReason.length() > 500) {
            throw new IllegalArgumentException(
                    "피드백 사유는 500자 이하여야 합니다."
            );
        }

        this.preference = preference;
        this.reason = normalizedReason == null
                || normalizedReason.isBlank()
                ? null
                : normalizedReason;
    }
}