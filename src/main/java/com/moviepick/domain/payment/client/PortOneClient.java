package com.moviepick.domain.payment.client;

import java.util.Optional;

public interface PortOneClient {

    /**
     * PortOne V2 결제 단건 조회 (GET /payments/{paymentId}).
     *
     * @return 결제 건. PortOne에 결제 건이 아직 없으면(404 PAYMENT_NOT_FOUND) empty
     * @throws com.moviepick.global.exception.BusinessException 호출 실패(PORTONE_REQUEST_FAILED) 또는 타임아웃(PORTONE_TIMEOUT)
     */
    Optional<PortOnePayment> getPayment(String paymentId);
}
