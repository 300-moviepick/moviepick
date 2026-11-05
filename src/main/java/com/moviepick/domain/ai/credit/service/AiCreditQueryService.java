package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.dto.response.AiCreditBalanceResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditTransactionResponse;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiCreditQueryService {

    private final AiCreditWalletRepository walletRepository;

    // [추가 ①] 거래 원장 조회 Repository
    private final AiCreditTransactionRepository transactionRepository;

    // 기존 크레딧 잔액 조회
    public AiCreditBalanceResponse getBalance(Long memberId) {

        AiCreditWallet wallet = walletRepository
                .findByMember_Id(memberId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.AI_WALLET_NOT_FOUND
                ));

        return AiCreditBalanceResponse.from(wallet);
    }

    // [추가 ②] 로그인 회원의 거래 내역 조회
    public PageResponse<AiCreditTransactionResponse> getTransactions(
            Long memberId,
            int page,
            int size
    ) {
        AiCreditWallet wallet = walletRepository
                .findByMember_Id(memberId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.AI_WALLET_NOT_FOUND
                ));

        // [추가 ③] 최신 거래부터 정렬
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<AiCreditTransaction> transactions =
                transactionRepository.findByWallet_Id(
                        wallet.getId(),
                        pageable
                );

        // [추가 ④] 기존 공통 페이징 응답 사용
        return PageResponse.from(
                transactions.map(AiCreditTransactionResponse::from)
        );
    }
}
