package com.moviepick.domain.payment.client;

import java.util.Arrays;

/**
 * PortOne 결제 단건 조회 결과 중 서버 검증에 필요한 값만 담는다.
 *
 * @param status      결제 상태
 * @param totalAmount 실제 결제 금액 (amount.total)
 * @param currency    통화 (예: "KRW")
 */
public record PortOnePayment(Status status, Long totalAmount, String currency) {

    /** PortOne V2 결제 상태. 문서에 없는 값이 오면 UNKNOWN으로 처리한다. */
    public enum Status {
        READY,
        PAY_PENDING,
        VIRTUAL_ACCOUNT_ISSUED,
        PAID,
        FAILED,
        PARTIAL_CANCELLED,
        CANCELLED,
        UNKNOWN;

        public static Status from(String value) {
            return Arrays.stream(values())
                    .filter(s -> s.name().equals(value))
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }
}
