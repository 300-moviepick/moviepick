package com.moviepick.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 오류가 발생했습니다."
    ),

    // 공통 오류 처리 (API 공통 규칙)
    PATH_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 주소를 찾을 수 없습니다."
    ),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "지원하지 않는 요청 방식입니다."
    ),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "지원하지 않는 요청 형식입니다. JSON으로 보내 주세요."
    ),
    CSRF_INVALID(
            HttpStatus.FORBIDDEN,
            "보안 확인 값(CSRF)이 없거나 올바르지 않습니다. 새로 받아 다시 시도해 주세요."
    ),
    CONCURRENT_REQUEST(
            HttpStatus.CONFLICT,
            "같은 요청이 동시에 처리되어 실패했습니다. 잠시 후 다시 시도해 주세요."
    ),
    INVALID_PAGE_REQUEST(
            HttpStatus.BAD_REQUEST,
            "페이지 번호는 0 이상, 페이지 크기는 1~100이어야 합니다."
    );

    private final HttpStatus status;
    private final String message;
}