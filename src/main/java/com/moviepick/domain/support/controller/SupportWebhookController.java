package com.moviepick.domain.support.controller;

import com.moviepick.domain.support.client.webhook.PortOneWebhookEvent;
import com.moviepick.domain.support.client.webhook.PortOneWebhookReceiver;
import com.moviepick.domain.support.service.SupportWebhookResult;
import com.moviepick.domain.support.service.SupportWebhookService;
import com.moviepick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PortOne 후원 결제 웹훅. PortOne 서버가 호출하므로 로그인·CSRF 없이 받고, 서명으로 검증한다.
 * (공개 등록: PublicEndpoints, CSRF 예외: SecurityConfig)
 */
@RestController
@RequestMapping("/api/webhooks/portone")
@RequiredArgsConstructor
public class SupportWebhookController {

    private final PortOneWebhookReceiver webhookReceiver;
    private final SupportWebhookService supportWebhookService;

    /** 서명 검증은 파싱 전 원문 기준이어야 하므로 본문을 바이트 그대로 받는다. 헤더 누락은 서명 검증 실패(401)로 처리한다. */
    @PostMapping("/support")
    public ResponseEntity<ApiResponse<SupportWebhookResult>> receiveSupport(
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp,
            @RequestHeader(value = "webhook-signature", required = false) String webhookSignature,
            @RequestBody(required = false) byte[] rawBody) {
        PortOneWebhookEvent event = webhookReceiver.receive(webhookId, webhookTimestamp, webhookSignature, rawBody);
        return ResponseEntity.ok(ApiResponse.ok(supportWebhookService.handle(event)));
    }
}
