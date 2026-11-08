package com.moviepick.domain.support.dto.response;

import com.moviepick.domain.payment.dto.response.ConfirmResult;
import com.moviepick.domain.payment.entity.PaymentStatus;

/**
 * 결제 확인 응답.
 *
 * @param status  확인 후 서버에 저장된 결제 상태
 * @param result  화면에 보여줄 결과 (COMPLETED 완료 / FAILED 실패 / PENDING 확인 중)
 * @param message result의 한글 설명
 */
public record SupportConfirmResponse(
        String paymentId,
        PaymentStatus status,
        ConfirmResult result,
        String message
) {
    public static SupportConfirmResponse of(String paymentId, PaymentStatus status, ConfirmResult result) {
        return new SupportConfirmResponse(paymentId, status, result, result.getDescription());
    }
}
