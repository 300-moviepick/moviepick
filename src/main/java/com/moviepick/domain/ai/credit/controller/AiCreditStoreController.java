package com.moviepick.domain.ai.credit.controller;

import com.moviepick.domain.ai.credit.dto.request.AiCreditPurchaseRequest;
import com.moviepick.domain.ai.credit.dto.response.AiCreditProductResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditPurchaseCreateResponse;
import com.moviepick.domain.ai.credit.service.AiCreditPurchaseService;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 크레딧 상품 목록·구매 생성. AI 추천이 켜져 있을 때(chat=openai)만 등록한다.
 * 꺼져 있으면 추천 API처럼 404 PATH_NOT_FOUND가 된다.
 * 이미 만든 결제의 확정(confirm)은 AiCreditPurchaseController가 스위치와 관계없이 처리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/credits")
@ConditionalOnProperty(
        name = "spring.ai.model.chat",
        havingValue = "openai"
)
public class AiCreditStoreController {

    private final AiCreditPurchaseService purchaseService;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<AiCreditProductResponse>>> getProducts() {
        return ResponseEntity.ok(ApiResponse.ok(purchaseService.getProducts()));
    }

    /** 구매 생성: 서버가 paymentId를 만들고 CREATED로 저장한다. 후원 생성과 같은 201 응답. */
    @PostMapping("/purchases")
    public ResponseEntity<ApiResponse<AiCreditPurchaseCreateResponse>> createPurchase(
            @LoginMember Long memberId,
            @Valid @RequestBody AiCreditPurchaseRequest request
    ) {
        AiCreditPurchaseCreateResponse response =
                purchaseService.createPurchase(memberId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
