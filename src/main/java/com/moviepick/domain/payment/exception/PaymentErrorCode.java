package com.moviepick.domain.payment.exception;

import com.moviepick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 결제사(PortOne) 연동 오류. 후원·AI 크레딧 결제가 함께 쓴다 (SupportErrorCode에서 이름 그대로 옮김). */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PORTONE_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "결제사 응답을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    PORTONE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "결제사 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
