package com.moviepick.domain.ai.credit.dto.response;

import com.moviepick.domain.ai.credit.entity.AiCreditWallet;

public record AiCreditBalanceResponse(
        int freeAvailable,
        int paidAvailable,
        long totalAvailable
) {

    // Entity를 API 응답 DTO로 변환
    public static AiCreditBalanceResponse from(AiCreditWallet wallet) {
        return new AiCreditBalanceResponse(
                wallet.getFreeAvailable(),
                wallet.getPaidAvailable(),
                (long) wallet.getFreeAvailable()
                        + wallet.getPaidAvailable()
        );
    }
}
