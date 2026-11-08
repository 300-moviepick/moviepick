package com.moviepick.domain.support.exception;

import com.moviepick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SupportErrorCode implements ErrorCode {

    INVALID_SUPPORT_AMOUNT(HttpStatus.BAD_REQUEST, "후원 금액은 3,000원, 6,000원, 9,000원 중 하나여야 합니다."),
    SUPPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 후원 결제입니다."),
    NOT_SUPPORT_OWNER(HttpStatus.FORBIDDEN, "본인의 후원 결제만 확인할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
