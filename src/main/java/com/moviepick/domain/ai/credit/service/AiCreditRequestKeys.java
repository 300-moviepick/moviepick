package com.moviepick.domain.ai.credit.service;

import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;

import java.util.UUID;

/**
 * AI 요청 한 건(requestKey)의 원장 멱등 키를 만든다.
 * 차감(USE)·확정(COMPLETE)·복구(RESTORE) 서비스가 같은 형식을 써야 서로의 기록을 찾을 수 있다.
 * 형식: {이벤트}:MEMBER:{회원ID}:REQ:{소문자 UUID}
 */
final class AiCreditRequestKeys {

    private AiCreditRequestKeys() {
    }

    // requestKey는 UUID 표준 형식만 받는다. 대소문자만 다른 키는 같은 요청으로 본다(소문자로 통일)
    static String normalize(String requestKey) {
        if (requestKey == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        try {
            UUID uuid = UUID.fromString(requestKey);

            if (!uuid.toString().equalsIgnoreCase(requestKey)) {
                throw new IllegalArgumentException("잘못된 UUID 형식");
            }

            return uuid.toString();

        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    static String useKey(Long memberId, String normalizedKey) {
        return "USE:MEMBER:" + memberId + ":REQ:" + normalizedKey;
    }

    static String completeKey(Long memberId, String normalizedKey) {
        return "COMPLETE:MEMBER:" + memberId + ":REQ:" + normalizedKey;
    }

    static String restoreKey(Long memberId, String normalizedKey) {
        return "RESTORE:MEMBER:" + memberId + ":REQ:" + normalizedKey;
    }
}
