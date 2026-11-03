package com.moviepick.domain.notification.service;

import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.notification.dto.response.NotificationResponse;
import com.moviepick.domain.notification.dto.response.ReadAllResponse;
import com.moviepick.domain.notification.dto.response.UnreadCountResponse;
import com.moviepick.domain.notification.entity.Notification;
import com.moviepick.domain.notification.exception.NotificationErrorCode;
import com.moviepick.domain.notification.repository.NotificationRepository;
import com.moviepick.domain.wishlist.entity.Wishlist;
import com.moviepick.domain.wishlist.repository.WishlistRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.response.PageResponse;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WishlistRepository wishlistRepository;

    // 최신순
    public PageResponse<NotificationResponse> getMyNotifications(Long memberId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        return PageResponse.from(notificationRepository
                .findByMemberId(memberId, pageable)
                .map(NotificationResponse::from));
    }

    public UnreadCountResponse getUnreadCount(Long memberId) {
        return new UnreadCountResponse(notificationRepository.countByMemberIdAndReadFalse(memberId));
    }

    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markRead();
    }

    @Transactional
    public ReadAllResponse markAllRead(Long memberId) {
        return new ReadAllResponse(notificationRepository.markAllRead(memberId));
    }

    /**
     * 해당 날짜에 개봉하는 찜 영화마다 회원별 개봉 알림을 1건씩 만든다.
     * 이미 같은 event_key 알림이 있는 회원은 건너뛰므로 여러 번 실행해도 중복되지 않는다.
     *
     * @return 새로 만든 알림 수
     */
    @Transactional
    public int createMovieReleaseNotifications(LocalDate releaseDate) {
        // 개봉일 기준: release_date_kr, 없으면 release_date (WishlistRepository.findReleaseTargets)
        List<Wishlist> targets = wishlistRepository.findReleaseTargets(releaseDate, MemberStatus.WITHDRAWN);
        if (targets.isEmpty()) {
            return 0;
        }

        Set<String> eventKeys = new HashSet<>();
        for (Wishlist wishlist : targets) {
            eventKeys.add(Notification.movieReleaseEventKey(wishlist.getMovie().getId(), releaseDate));
        }
        Set<String> alreadySent = new HashSet<>();
        for (Object[] row : notificationRepository.findMemberIdAndEventKeys(eventKeys)) {
            alreadySent.add(sentKey((Long) row[0], (String) row[1]));
        }

        List<Notification> notifications = targets.stream()
                .map(wishlist -> Notification.movieRelease(wishlist.getMember(), wishlist.getMovie(), releaseDate))
                .filter(n -> !alreadySent.contains(sentKey(n.getMember().getId(), n.getEventKey())))
                .toList();

        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    private static String sentKey(Long memberId, String eventKey) {
        return memberId + "|" + eventKey;
    }
}
