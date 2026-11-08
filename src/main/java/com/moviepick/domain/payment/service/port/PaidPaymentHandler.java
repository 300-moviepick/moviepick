package com.moviepick.domain.payment.service.port;

import com.moviepick.domain.payment.client.PortOnePayment;
import com.moviepick.domain.payment.entity.Payment;
import com.moviepick.domain.payment.entity.PaymentStatus;
import com.moviepick.domain.payment.entity.ProductType;

/**
 * 상품 종류별 결제 완료 처리. 웹훅(SupportWebhookService)이 결제 건의 상품 종류로 구현체를 골라 부른다.
 *
 * <p>후원(SITE_SUPPORT)은 support 도메인이, AI 크레딧(AI_CREDIT)은 ai 도메인이 구현한다.
 * support는 이 인터페이스만 알고 ai를 모른다 (의존 방향: ai → support).
 */
public interface PaidPaymentHandler {

    ProductType productType();

    /**
     * PortOne 재조회 결과를 반영한다. confirm과 같은 로직을 써야 한다(조건부 UPDATE로 한 번만 반영).
     *
     * @return 반영 후 결제 상태 (PAID: 반영됨/이미 반영, FAILED: 금액 불일치, CREATED: 미완료)
     */
    PaymentStatus applyPaid(Payment payment, PortOnePayment portOnePayment);
}
