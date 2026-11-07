package com.moviepick.domain.ai.credit.controller;

import com.moviepick.domain.ai.credit.dto.response.AiCreditPurchaseConfirmResponse;
import com.moviepick.domain.ai.credit.service.AiCreditPurchaseService;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 크레딧 구매 확정. AI 스위치와 관계없이 항상 등록한다 (돈을 낸 결제는 반드시 지급).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/credits/purchases")
public class AiCreditPurchaseController {

    private final AiCreditPurchaseService purchaseService;

    /** 결제 확인: 요청 본문 없이 paymentId만 받고, PortOne에 직접 조회해서 판단한다. */
    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<ApiResponse<AiCreditPurchaseConfirmResponse>> confirm(
            @LoginMember Long memberId,
            @PathVariable String paymentId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseService.confirm(memberId, paymentId)));
    }
}
