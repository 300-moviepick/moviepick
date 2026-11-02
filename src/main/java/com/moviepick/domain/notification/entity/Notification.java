package com.moviepick.domain.notification.entity;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = Notification.UK_MEMBER_EVENT_KEY,
                columnNames = {"member_id", "event_key"}),
        indexes = {
                @Index(name = "idx_notifications_member_id_is_read", columnList = "member_id, is_read"),
                @Index(name = "idx_notifications_movie_id", columnList = "movie_id")
        })
public class Notification extends BaseEntity {

    // 같은 사건으로 같은 회원에게 알림이 두 번 생기지 않게 한다
    public static final String UK_MEMBER_EVENT_KEY = "uk_notifications_member_id_event_key";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    // 영화 관련 알림이 아니면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    // 중복 방지 키. 예: MOVIE_RELEASE:{movieId}:{개봉일}
    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    public static Notification movieRelease(Member member, Movie movie, LocalDate releaseDate) {
        Notification notification = new Notification();
        notification.member = member;
        notification.type = NotificationType.MOVIE_RELEASE;
        notification.movie = movie;
        notification.title = "찜한 영화 개봉 알림";
        notification.message = "찜한 영화 '" + movie.getTitle() + "'가 오늘 개봉했어요!";
        notification.eventKey = movieReleaseEventKey(movie.getId(), releaseDate);
        return notification;
    }

    public static String movieReleaseEventKey(Long movieId, LocalDate releaseDate) {
        return NotificationType.MOVIE_RELEASE.name() + ":" + movieId + ":" + releaseDate;
    }

    public void markRead() {
        this.read = true;
    }
}
