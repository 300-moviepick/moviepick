package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.exception.AiErrorCode;
import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiCreditCompleteService {

    private final AiCreditWalletRepository walletRepository;
    private final AiCreditTransactionRepository transactionRepository;

    // AI 추천 성공 시 크레딧 사용을 확정한다.
    @Transactional
    public CompleteResult completeOne(Long memberId, String requestKey) {

        String normalizedKey = AiCreditRequestKeys.normalize(requestKey);

        // ① 다른 사용·복구 요청과 동일한 지갑 잠금 사용
        AiCreditWallet wallet = walletRepository
                .findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        AiErrorCode.AI_WALLET_NOT_FOUND
                ));

        String useKey =
                AiCreditRequestKeys.useKey(memberId, normalizedKey);

        String completeKey =
                AiCreditRequestKeys.completeKey(memberId, normalizedKey);

        String restoreKey =
                AiCreditRequestKeys.restoreKey(memberId, normalizedKey);

        // ② 실제 사용 내역 확인
        AiCreditTransaction originalUse = transactionRepository
                .findByIdempotencyKey(useKey)
                .orElseThrow(() -> new BusinessException(
                        AiErrorCode.AI_CREDIT_USE_NOT_FOUND
                ));

        if (originalUse.getEventType() != AiCreditEventType.USE
                || !Objects.equals(
                originalUse.getWallet().getId(),
                wallet.getId()
        )) {
            throw new BusinessException(
                    AiErrorCode.AI_CREDIT_USE_NOT_FOUND
            );
        }

        // ③ 이미 복구된 요청은 성공 확정할 수 없다.
        if (transactionRepository.existsByIdempotencyKey(restoreKey)) {
            throw new BusinessException(
                    AiErrorCode.AI_CREDIT_REQUEST_ALREADY_FINALIZED
            );
        }

        // ④ 이미 완료된 요청은 중복 기록하지 않는다.
        if (transactionRepository.existsByIdempotencyKey(completeKey)) {
            return new CompleteResult(true);
        }

        // ⑤ COMPLETE 거래 기록 (잔액 변경 없음)
        transactionRepository.saveAndFlush(
                AiCreditTransaction.complete(originalUse, completeKey)
        );

        return new CompleteResult(false);
    }

    public record CompleteResult(boolean duplicate) {
    }
}
