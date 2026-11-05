package com.moviepick.domain.review.entity;

public enum ReviewStatus {
    VISIBLE,   // 노출 중
    DELETED,   // 작성자가 삭제 (재작성 시 덮어써서 VISIBLE로 복구)
    BLINDED    // 관리자가 숨김 (재작성 불가)
}