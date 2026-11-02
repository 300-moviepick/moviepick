package com.moviepick.domain.notification.exception;

import com.moviepick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    // 없거나 본인 알림이 아닌 경우 모두 404
    NOTIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "알림을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String message;
}
