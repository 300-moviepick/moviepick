package com.moviepick.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.domain.notification.dto.response.NotificationResponse;
import com.moviepick.domain.notification.entity.Notification;
import com.moviepick.domain.notification.entity.NotificationType;
import com.moviepick.domain.notification.exception.NotificationErrorCode;
import com.moviepick.domain.notification.repository.NotificationRepository;
import com.moviepick.domain.wishlist.entity.Wishlist;
import com.moviepick.domain.wishlist.repository.WishlistRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.response.PageResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 서비스 트랜잭션이 실제로 커밋되는지 보려고 테스트 트랜잭션을 끄고 H2로 확인한다. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, NotificationService.class})
class NotificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private WishlistRepository wishlistRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private Member member;
    private Member other;
    private Movie releasingMovie;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.createLocal("member@test.com", "encoded", "member"));
        other = memberRepository.save(Member.createLocal("other@test.com", "encoded", "other"));
        releasingMovie = saveMovie(1L, "영화 A", TODAY);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
        wishlistRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("오늘 개봉하는 찜 영화마다 회원별 알림이 1건 생기고, 다시 실행해도 중복되지 않는다")
    void createMovieReleaseNotifications() {
        Movie notToday = saveMovie(2L, "영화 B", TODAY.plusDays(1));
        Member withdrawn = memberRepository.save(Member.createLocal("gone@test.com", "encoded", "gone"));
        withdrawn.withdraw();
        memberRepository.save(withdrawn);
        wishlistRepository.save(Wishlist.create(member, releasingMovie));
        wishlistRepository.save(Wishlist.create(other, releasingMovie));
        wishlistRepository.save(Wishlist.create(member, notToday));
        wishlistRepository.save(Wishlist.create(withdrawn, releasingMovie));

        int created = notificationService.createMovieReleaseNotifications(TODAY);
        int createdAgain = notificationService.createMovieReleaseNotifications(TODAY);

        assertThat(created).isEqualTo(2);
        assertThat(createdAgain).isZero();
        assertThat(notificationRepository.count()).isEqualTo(2);

        NotificationResponse response = notificationService.getMyNotifications(member.getId(), 0, 20)
                .content().get(0);
        assertThat(response.type()).isEqualTo(NotificationType.MOVIE_RELEASE);
        assertThat(response.movieId()).isEqualTo(releasingMovie.getId());
        assertThat(response.title()).isEqualTo("찜한 영화 개봉 알림");
        assertThat(response.message()).isEqualTo("찜한 영화 '영화 A'가 오늘 개봉했어요!");
        assertThat(response.read()).isFalse();
    }

    @Test
    @DisplayName("한국 개봉일이 있으면 한국 개봉일 기준으로 알림을 만들고, 없으면 기본 개봉일을 쓴다")
    void releaseDateKrFirst() {
        // 기본 개봉일은 다른 날이지만 한국 개봉일이 오늘 → 알림
        Movie koreaToday = saveMovie(2L, "한국 오늘", TODAY.minusDays(10));
        ReflectionTestUtils.setField(koreaToday, "releaseDateKr", TODAY);
        movieRepository.save(koreaToday);
        // 기본 개봉일은 오늘이지만 한국 개봉일이 다음 주 → 알림 없음
        Movie koreaLater = saveMovie(3L, "한국 나중", TODAY);
        ReflectionTestUtils.setField(koreaLater, "releaseDateKr", TODAY.plusDays(7));
        movieRepository.save(koreaLater);
        wishlistRepository.save(Wishlist.create(member, koreaToday));
        wishlistRepository.save(Wishlist.create(member, koreaLater));
        // releasingMovie: 한국 개봉일 없음, 기본 개봉일 오늘 → 알림
        wishlistRepository.save(Wishlist.create(member, releasingMovie));

        int created = notificationService.createMovieReleaseNotifications(TODAY);

        assertThat(created).isEqualTo(2);
        assertThat(notificationService.getMyNotifications(member.getId(), 0, 20).content())
                .extracting(NotificationResponse::movieId)
                .containsExactlyInAnyOrder(koreaToday.getId(), releasingMovie.getId());
    }

    @Test
    @DisplayName("내 알림 목록은 최신순이고 다른 회원 알림은 보이지 않는다")
    void getMyNotifications() {
        Movie second = saveMovie(2L, "영화 B", TODAY);
        Notification first = notificationRepository.save(Notification.movieRelease(member, releasingMovie, TODAY));
        Notification latest = notificationRepository.save(Notification.movieRelease(member, second, TODAY));
        notificationRepository.save(Notification.movieRelease(other, releasingMovie, TODAY));

        PageResponse<NotificationResponse> response = notificationService.getMyNotifications(member.getId(), 0, 20);

        assertThat(response.content())
                .extracting(NotificationResponse::notificationId)
                .containsExactly(latest.getId(), first.getId());
    }

    @Test
    @DisplayName("알림 하나를 읽으면 읽지 않은 수가 줄고, 남의 알림이나 없는 알림은 404다")
    void markRead() {
        Notification mine = notificationRepository.save(Notification.movieRelease(member, releasingMovie, TODAY));
        Notification others = notificationRepository.save(Notification.movieRelease(other, releasingMovie, TODAY));

        notificationService.markRead(member.getId(), mine.getId());

        assertThat(notificationService.getUnreadCount(member.getId()).unreadCount()).isZero();
        assertNotFound(() -> notificationService.markRead(member.getId(), others.getId()));
        assertNotFound(() -> notificationService.markRead(member.getId(), 9999L));
        assertThat(notificationService.getUnreadCount(other.getId()).unreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 읽음은 내 안 읽은 알림만 바꾸고 바뀐 수를 돌려준다")
    void markAllRead() {
        Movie second = saveMovie(2L, "영화 B", TODAY);
        Notification alreadyRead = notificationRepository.save(Notification.movieRelease(member, releasingMovie, TODAY));
        notificationRepository.save(Notification.movieRelease(member, second, TODAY));
        notificationRepository.save(Notification.movieRelease(other, releasingMovie, TODAY));
        notificationService.markRead(member.getId(), alreadyRead.getId());

        int updated = notificationService.markAllRead(member.getId()).updatedCount();

        assertThat(updated).isEqualTo(1);
        assertThat(notificationService.getUnreadCount(member.getId()).unreadCount()).isZero();
        assertThat(notificationService.getUnreadCount(other.getId()).unreadCount()).isEqualTo(1);
    }

    private Movie saveMovie(Long tmdbId, String title, LocalDate releaseDate) {
        Movie movie = new Movie(tmdbId, title);
        ReflectionTestUtils.setField(movie, "releaseDate", releaseDate);
        return movieRepository.save(movie);
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
