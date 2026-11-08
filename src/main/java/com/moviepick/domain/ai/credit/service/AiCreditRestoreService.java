package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.exception.AiErrorCode;
import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
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
public class AiCreditRestoreService {

    private final AiCreditWalletRepository walletRepository;
    private final AiCreditTransactionRepository transactionRepository;

    // AI 추천 실패 시 기존 사용 크레딧을 복구
    @Transactional
    public RestoreResult restoreOne(Long memberId, String requestKey) {

        String normalizedKey = AiCreditRequestKeys.normalize(requestKey);

        // ① 지갑 잠금
        AiCreditWallet wallet = walletRepository
                .findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        AiErrorCode.AI_WALLET_NOT_FOUND
                ));

        String useKey =
                AiCreditRequestKeys.useKey(memberId, normalizedKey);

        String restoreKey =
                AiCreditRequestKeys.restoreKey(memberId, normalizedKey);

        // ② 원래 크레딧 사용 기록 확인
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

        // 이미 성공 확정된 요청은 복구할 수 없다.
        String completeKey =
                AiCreditRequestKeys.completeKey(memberId, normalizedKey);

        if (transactionRepository.existsByIdempotencyKey(completeKey)) {
            throw new BusinessException(
                    AiErrorCode.AI_CREDIT_REQUEST_ALREADY_FINALIZED
            );
        }

        // 기존 중복 복구 방지
        if (transactionRepository.existsByIdempotencyKey(restoreKey)) {
            return new RestoreResult(
                    originalUse.getBucket(),
                    wallet.getFreeAvailable(),
                    wallet.getPaidAvailable(),
                    true
            );
        }

        // ④ 원래 사용한 크레딧 종류로 복구
        AiCreditBucket bucket = originalUse.getBucket();
        wallet.restoreOneCredit(bucket);

        // ⑤ 복구 거래 원장 기록
        transactionRepository.saveAndFlush(
                AiCreditTransaction.restore(originalUse, restoreKey)
        );

        return new RestoreResult(
                bucket,
                wallet.getFreeAvailable(),
                wallet.getPaidAvailable(),
                false
        );
    }

    public record RestoreResult(
            AiCreditBucket bucket,
            int freeAvailable,
            int paidAvailable,
            boolean duplicate
    ) {
    }
}
