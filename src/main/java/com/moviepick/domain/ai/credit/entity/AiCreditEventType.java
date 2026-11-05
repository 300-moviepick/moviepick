package com.moviepick.domain.ai.credit.entity;

public enum AiCreditEventType {
    FREE_GRANT,  // 무료 지급
    PURCHASE,    // 유료 충전
    USE,         // AI 추천 사용
    COMPLETE,    // 추천 성공
    RESTORE      // 실패 복구
}
