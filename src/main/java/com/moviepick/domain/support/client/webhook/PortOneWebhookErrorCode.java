package com.moviepick.domain.support.client.webhook;

import com.moviepick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** PortOne 웹훅 공통 오류. 결제 유형과 무관하게 모든 웹훅 엔드포인트가 같이 쓴다. */
@Getter
@RequiredArgsConstructor
public enum PortOneWebhookErrorCode implements ErrorCode {

    // 헤더 누락, 서명 불일치, 허용 시간 초과, 시크릿 미설정을 구분하지 않고 응답한다 (사유는 서버 로그에만 남긴다)
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "웹훅 서명을 검증하지 못했습니다."),
    INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "웹훅 본문을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
