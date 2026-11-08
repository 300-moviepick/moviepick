package com.moviepick.domain.ai.exception;

import com.moviepick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** AI 추천·크레딧 오류. errorCode 응답 값은 상수 이름이다 (CommonErrorCode에서 이름 그대로 옮김). */
@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {

    // 크레딧 지갑·사용·복구
    AI_WALLET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "AI 크레딧 지갑을 찾을 수 없습니다."
    ),
    AI_CREDIT_INSUFFICIENT(
            HttpStatus.CONFLICT,
            "AI 크레딧이 부족합니다."
    ),
    AI_CREDIT_USE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "복구할 AI 크레딧 사용 내역이 없습니다."
    ),
    AI_CREDIT_REQUEST_ALREADY_FINALIZED(
            HttpStatus.CONFLICT,
            "이미 완료되거나 복구된 AI 요청입니다."
    ),

    // 추천
    AI_RECOMMENDATION_NO_CANDIDATES(
            HttpStatus.NOT_FOUND,
            "추천 가능한 영화가 없습니다."
    ),
    AI_RECOMMENDATION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "이미 처리 중인 AI 추천 요청입니다."
    ),
    AI_RECOMMENDATION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "AI 추천 생성에 실패했습니다. 사용한 크레딧은 복구되었습니다."
    ),

    // 크레딧 구매
    AI_CREDIT_PRODUCT_NOT_FOUND(
            HttpStatus.BAD_REQUEST,
            "존재하지 않는 AI 크레딧 상품입니다."
    ),
    AI_CREDIT_PURCHASE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 AI 크레딧 구매입니다."
    ),
    NOT_AI_CREDIT_PURCHASE_OWNER(
            HttpStatus.FORBIDDEN,
            "본인의 AI 크레딧 구매만 확인할 수 있습니다."
    ),
    AI_CREDIT_PRICE_UNMAPPED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "결제 금액에 맞는 AI 크레딧 상품이 없습니다. 운영자에게 문의해 주세요."
    );

    private final HttpStatus status;
    private final String message;
}
