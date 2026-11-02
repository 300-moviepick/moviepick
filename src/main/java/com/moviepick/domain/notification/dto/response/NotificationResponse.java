package com.moviepick.domain.notification.dto.response;

import com.moviepick.domain.notification.entity.Notification;
import com.moviepick.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        Long movieId,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                // 프록시 초기화 없이 FK 값만 읽는다
                notification.getMovie() == null ? null : notification.getMovie().getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
