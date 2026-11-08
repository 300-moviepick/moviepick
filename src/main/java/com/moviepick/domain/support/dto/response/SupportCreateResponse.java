package com.moviepick.domain.support.dto.response;

import com.moviepick.domain.payment.entity.Payment;

/**
 * 후원 생성 응답. 프론트는 이 값으로 PortOne 결제창(SDK requestPayment)을 연다.
 * storeId, channelKey는 공개 식별자라 응답에 포함한다. (API Secret은 절대 포함하지 않는다)
 */
public record SupportCreateResponse(
        String paymentId,
        long amount,
        String orderName,
        String storeId,
        String channelKey
) {
    public static SupportCreateResponse of(Payment payment, String orderName, String storeId, String channelKey) {
        return new SupportCreateResponse(
                payment.getPaymentId(),
                payment.getAmount(),
                orderName,
                storeId,
                channelKey);
    }
}
