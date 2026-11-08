package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.exception.AiErrorCode;
import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class AiCreditSpendService {

    private final AiCreditWalletRepository walletRepository;
    private final AiCreditTransactionRepository transactionRepository;

    // AI 추천 요청 1건당 크레딧 1회 사용
    @Transactional
    public SpendResult spendOne(Long memberId, String requestKey) {

        String normalizedKey = AiCreditRequestKeys.normalize(requestKey);

        // ① 지갑 쓰기 잠금 획득
        AiCreditWallet wallet = walletRepository
                .findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        AiErrorCode.AI_WALLET_NOT_FOUND
                ));

        String idempotencyKey =
                AiCreditRequestKeys.useKey(memberId, normalizedKey);

        // ② 동일한 요청으로 이미 사용했다면 재차감하지 않는다.
        var existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {

            // 이미 종료된 요청 키는 다시 사용하지 않는다.
            String completeKey =
                    AiCreditRequestKeys.completeKey(memberId, normalizedKey);

            String restoreKey =
                    AiCreditRequestKeys.restoreKey(memberId, normalizedKey);

            if (transactionRepository.existsByIdempotencyKey(completeKey)
                    || transactionRepository.existsByIdempotencyKey(restoreKey)) {
                throw new BusinessException(
                        AiErrorCode.AI_CREDIT_REQUEST_ALREADY_FINALIZED
                );
            }

            return new SpendResult(
                    existing.get().getBucket(),
                    wallet.getFreeAvailable(),
                    wallet.getPaidAvailable(),
                    true
            );
        }

        // ③ 무료 우선, 없으면 유료 크레딧 차감
        AiCreditBucket bucket = wallet.useOneCredit();

        // ④ USE -1 거래 원장 기록
        AiCreditTransaction transaction = AiCreditTransaction.use(
                wallet,
                bucket,
                normalizedKey,
                idempotencyKey
        );

        transactionRepository.saveAndFlush(transaction);

        return new SpendResult(
                bucket,
                wallet.getFreeAvailable(),
                wallet.getPaidAvailable(),
                false
        );
    }

    // 현재는 내부 서비스 반환값으로 사용
    public record SpendResult(
            AiCreditBucket bucket,
            int freeAvailable,
            int paidAvailable,
            boolean duplicate
    ) {
    }
}
