package com.moviepick.domain.payment.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConfirmResult {

    COMPLETED("완료"),
    FAILED("실패"),
    PENDING("확인 중");

    private final String description;
}
