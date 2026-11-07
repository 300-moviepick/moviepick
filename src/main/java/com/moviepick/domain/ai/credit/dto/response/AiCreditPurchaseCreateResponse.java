package com.moviepick.domain.ai.credit.dto.response;

import com.moviepick.domain.support.entity.Payment;

/**
 * 구매 생성 응답. 후원 생성 응답(SupportCreateResponse)과 같은 모양이다.
 * 프론트는 이 값으로 PortOne 결제창을 연다. storeId, channelKey는 공개 식별자다.
 */
public record AiCreditPurchaseCreateResponse(
        String paymentId,
        long amount,
        String orderName,
        String storeId,
        String channelKey
) {

    public static AiCreditPurchaseCreateResponse of(
            Payment payment,
            String orderName,
            String storeId,
            String channelKey
    ) {
        return new AiCreditPurchaseCreateResponse(
                payment.getPaymentId(),
                payment.getAmount(),
                orderName,
                storeId,
                channelKey
        );
    }
}
