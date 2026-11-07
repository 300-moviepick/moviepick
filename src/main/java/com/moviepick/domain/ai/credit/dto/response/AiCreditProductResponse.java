package com.moviepick.domain.ai.credit.dto.response;

import com.moviepick.domain.ai.credit.entity.AiCreditProduct;

// 구매 가능한 AI 크레딧 상품
public record AiCreditProductResponse(
        String productCode,
        int credits,
        long amount,
        String orderName
) {

    public static AiCreditProductResponse from(AiCreditProduct product) {
        return new AiCreditProductResponse(
                product.name(),
                product.getCredits(),
                product.getAmount(),
                product.getOrderName()
        );
    }
}
