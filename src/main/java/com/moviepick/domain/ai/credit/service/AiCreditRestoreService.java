package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiCreditRestoreService {

    private final AiCreditWalletRepository walletRepository;
    private final AiCreditTransactionRepository transactionRepository;

    // AI 추천 실패 시 기존 사용 크레딧을 복구
    @Transactional
    public RestoreResult restoreOne(Long memberId, String requestKey) {

        String normalizedKey = normalizeRequestKey(requestKey);

        // ① 지갑 잠금
        AiCreditWallet wallet = walletRepository
                .findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.AI_WALLET_NOT_FOUND
                ));

        String useKey =
                "USE:MEMBER:" + memberId + ":REQ:" + normalizedKey;

        String restoreKey =
                "RESTORE:MEMBER:" + memberId + ":REQ:" + normalizedKey;

        // ② 원래 크레딧 사용 기록 확인
        AiCreditTransaction originalUse = transactionRepository
                .findByIdempotencyKey(useKey)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.AI_CREDIT_USE_NOT_FOUND
                ));

        if (originalUse.getEventType() != AiCreditEventType.USE
                || !Objects.equals(
                originalUse.getWallet().getId(),
                wallet.getId()
        )) {
            throw new BusinessException(
                    CommonErrorCode.AI_CREDIT_USE_NOT_FOUND
            );
        }

        // [추가] 이미 성공 확정된 요청은 복구할 수 없다.
        String completeKey =
                "COMPLETE:MEMBER:" + memberId + ":REQ:" + normalizedKey;

        if (transactionRepository.existsByIdempotencyKey(completeKey)) {
            throw new BusinessException(
                    CommonErrorCode.AI_CREDIT_REQUEST_ALREADY_FINALIZED
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

    private String normalizeRequestKey(String requestKey) {

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

    public record RestoreResult(
            AiCreditBucket bucket,
            int freeAvailable,
            int paidAvailable,
            boolean duplicate
    ) {
    }
}