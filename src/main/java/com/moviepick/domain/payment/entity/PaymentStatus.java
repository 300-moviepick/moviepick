package com.moviepick.domain.payment.entity;

public enum PaymentStatus {
    CREATED,    // 결제 건 생성(후원·AI 크레딧), 결제 전
    PAID,       // 결제 완료 (PortOne 조회로 확인됨)
    FAILED,     // 결제 실패 (PortOne 결제 금액·통화가 서버 기록과 다름)
    EXPIRED,    // 미결제 만료 (아직 쓰지 않음. 오래된 CREATED를 정리하는 만료 배치에서 쓸 예정)
    CANCELLED   // 결제 취소 (아직 쓰지 않음. PortOne 취소 웹훅 처리에서 쓸 예정)
}
