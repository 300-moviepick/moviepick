package com.moviepick.domain.ai.credit.dto.response;

import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;

import java.time.LocalDateTime;

public record AiCreditTransactionResponse(
        Long id,
        AiCreditEventType eventType,
        AiCreditBucket bucket,
        int delta,
        Long paymentId,
        String requestKey,
        LocalDateTime createdAt
) {

    // 거래 원장을 응답 DTO로 변환
    public static AiCreditTransactionResponse from(
            AiCreditTransaction transaction
    ) {
        Long paymentId = transaction.getPayment() == null
                ? null
                : transaction.getPayment().getId();

        return new AiCreditTransactionResponse(
                transaction.getId(),
                transaction.getEventType(),
                transaction.getBucket(),
                transaction.getDelta(),
                paymentId,
                transaction.getRequestKey(),
                transaction.getCreatedAt()
        );
    }
}
