package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.exception.AiErrorCode;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.domain.payment.entity.Payment;
import com.moviepick.domain.payment.repository.PaymentRepository;
import com.moviepick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 결제 완료 반영 + 유료 크레딧 지급을 한 트랜잭션으로 처리한다. confirm과 웹훅이 이 메서드 하나만 쓴다.
 *
 * <p>중복 지급 방지: ① 결제 행 조건부 UPDATE(CREATED → PAID, 0행이면 이미 처리됨)
 * ② 원장 멱등 키 PURCHASE:{paymentId}(UK). 어느 단계가 실패해도 결제 상태까지 롤백된다.
 * 호출하는 쪽은 PortOne 재조회를 이 트랜잭션 밖에서 끝내고, 지갑은 미리 만들어 둔다(ensureWallet).
 */
@Service
@RequiredArgsConstructor
public class AiCreditPurchaseGrantService {

    private final PaymentRepository paymentRepository;
    private final AiCreditWalletRepository walletRepository;
    private final AiCreditTransactionRepository transactionRepository;

    /**
     * @return 이번 호출이 PAID로 바꾸고 지급했으면 true, 이미 처리된 결제면 false
     */
    @Transactional
    public boolean markPaidAndGrant(String paymentId, Long memberId, int credits) {

        // ① CREATED일 때만 PAID (Repository 트랜잭션이 이 트랜잭션에 합류)
        if (paymentRepository.markPaid(paymentId, LocalDateTime.now()) == 0) {
            return false;
        }

        // ② 지갑 잠금 후 유료 크레딧 증가
        AiCreditWallet wallet = walletRepository
                .findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        AiErrorCode.AI_WALLET_NOT_FOUND
                ));
        wallet.grantPaidCredits(credits);

        // ③ PURCHASE 원장 (결제 건 연결, 멱등 키)
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(
                        AiErrorCode.AI_CREDIT_PURCHASE_NOT_FOUND
                ));

        transactionRepository.saveAndFlush(
                AiCreditTransaction.purchase(
                        wallet,
                        payment,
                        credits,
                        "PURCHASE:" + paymentId
                )
        );

        return true;
    }
}
