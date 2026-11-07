package com.moviepick.domain.ai.credit.dto.request;

import jakarta.validation.constraints.NotBlank;

// 구매 생성 요청. 상품 코드만 받는다 (금액·크레딧 수는 서버 가격표 값)
public record AiCreditPurchaseRequest(
        @NotBlank(message = "상품 코드는 필수입니다.")
        String productCode
) {
}
