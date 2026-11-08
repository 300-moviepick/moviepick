package com.moviepick.domain.ai.credit.dto.response;

import com.moviepick.domain.payment.dto.response.ConfirmResult;
import com.moviepick.domain.payment.entity.PaymentStatus;

/**
 * 구매 확정 응답. 앞 네 필드는 후원 확인 응답(SupportConfirmResponse)과 같고, 확정 후 크레딧 잔액을 붙인다.
 *
 * @param status  확인 후 서버에 저장된 결제 상태
 * @param result  화면에 보여줄 결과 (COMPLETED 완료 / FAILED 실패 / PENDING 확인 중)
 * @param message result의 한글 설명
 * @param credits 응답 시점의 크레딧 잔액
 */
public record AiCreditPurchaseConfirmResponse(
        String paymentId,
        PaymentStatus status,
        ConfirmResult result,
        String message,
        AiCreditBalanceResponse credits
) {

    public static AiCreditPurchaseConfirmResponse of(
            String paymentId,
            PaymentStatus status,
            ConfirmResult result,
            AiCreditBalanceResponse credits
    ) {
        return new AiCreditPurchaseConfirmResponse(
                paymentId, status, result, result.getDescription(), credits
        );
    }
}
