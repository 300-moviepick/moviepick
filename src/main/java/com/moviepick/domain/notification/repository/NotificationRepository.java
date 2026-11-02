package com.moviepick.domain.notification.repository;

import com.moviepick.domain.notification.entity.Notification;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByMemberId(Long memberId, Pageable pageable);

    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

    long countByMemberIdAndReadFalse(Long memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n
            set n.read = true, n.updatedAt = CURRENT_TIMESTAMP
            where n.member.id = :memberId and n.read = false
            """)
    int markAllRead(@Param("memberId") Long memberId);

    // 이미 만든 알림 확인용: [회원 ID, 이벤트 키]
    @Query("""
            select n.member.id, n.eventKey from Notification n
            where n.eventKey in :eventKeys
            """)
    List<Object[]> findMemberIdAndEventKeys(@Param("eventKeys") Collection<String> eventKeys);
}
