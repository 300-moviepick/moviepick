package com.moviepick.domain.payment.service;

/** 결제 웹훅(후원·AI 크레딧) 처리 결과. 모두 200으로 응답한다 (재전송이 필요 없는 결과). */
public enum SupportWebhookResult {
    PAID_APPLIED,       // 처리 후 결제가 PAID (이번 요청이 반영했거나, 같은 순간 다른 요청이 반영)
    ALREADY_PROCESSED,  // 이미 CREATED가 아님 (PAID / FAILED / EXPIRED / CANCELLED)
    NOT_PAID,           // PortOne 재조회 결과가 결제 완료가 아님
    AMOUNT_MISMATCH,    // 재조회 금액·통화가 DB 기록과 달라 FAILED로 처리
    NOT_FOUND,          // 우리 DB에 없는 결제
    IGNORED             // 처리 대상이 아닌 이벤트 또는 후원이 아닌 결제
}
