package com.moviepick.domain.notification.controller;

import com.moviepick.domain.notification.dto.response.NotificationResponse;
import com.moviepick.domain.notification.dto.response.ReadAllResponse;
import com.moviepick.domain.notification.dto.response.UnreadCountResponse;
import com.moviepick.domain.notification.service.NotificationService;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import com.moviepick.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 내 알림 목록 (최신순) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMyNotifications(
            @LoginMember Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getMyNotifications(memberId, page, size)));
    }

    /** 읽지 않은 알림 수 */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getUnreadCount(memberId)));
    }

    /** 알림 읽음 처리 */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable Long notificationId,
            @LoginMember Long memberId) {
        notificationService.markRead(memberId, notificationId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 전체 알림 읽음 처리 */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<ReadAllResponse>> markAllRead(
            @LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markAllRead(memberId)));
    }
}
