package com.moviepick.domain.ai.credit.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * AI 크레딧 상품 가격표 (서버 기준, KRW).
 *
 * <p>클라이언트는 상품 코드만 보낸다. 금액·크레딧 수는 항상 이 표의 값을 쓴다.
 * 결제 건(payments)에는 상품 코드 컬럼이 없으므로 지급할 크레딧 수는 결제 금액으로 역조회한다.
 * 가격은 상품마다 달라야 한다(1:1). 가격을 바꿀 때는 기존 금액으로 만든 미확정 결제가 남아 있을 수 있으니
 * 옛 금액을 바로 지우지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum AiCreditProduct {

    CREDIT_10(1_000L, 10, "MoviePick AI 크레딧 10회"),
    CREDIT_30(2_500L, 30, "MoviePick AI 크레딧 30회"),
    CREDIT_50(4_000L, 50, "MoviePick AI 크레딧 50회");

    private final long amount;
    private final int credits;
    private final String orderName;

    public static Optional<AiCreditProduct> fromCode(String code) {
        return Arrays.stream(values())
                .filter(product -> product.name().equals(code))
                .findFirst();
    }

    public static Optional<AiCreditProduct> fromAmount(long amount) {
        return Arrays.stream(values())
                .filter(product -> product.amount == amount)
                .findFirst();
    }
}
