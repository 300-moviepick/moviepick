package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.domain.member.entity.Member;

import com.moviepick.domain.member.service.port.SignupBenefitPort;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiSignupCreditService implements SignupBenefitPort {

    private static final int INITIAL_FREE_CREDITS = 3;

    private final AiCreditWalletRepository walletRepository;
    private final AiCreditTransactionRepository transactionRepository;

    // 회원 도메인의 SignupBenefitPort 구현. 회원가입 트랜잭션에 참여한다
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void grantTo(Member savedMember) {
        Objects.requireNonNull(savedMember, "회원 정보가 필요합니다.");

        Long memberId = savedMember.getId();

        if (memberId == null) {
            throw new IllegalArgumentException(
                    "저장된 회원만 지급할 수 있습니다."
            );
        }

        String key = "FREE_GRANT:MEMBER:" + memberId;

        // 이미 지급한 회원은 중복 지급하지 않음
        if (transactionRepository.existsByIdempotencyKey(key)) {
            return;
        }

        AiCreditWallet wallet = walletRepository.findByMember_Id(memberId)
                .orElseGet(() ->
                        walletRepository.save(
                                new AiCreditWallet(savedMember)
                        )
                );

        wallet.grantFreeCredits(INITIAL_FREE_CREDITS);

        // 지갑 잔액과 거래 원장을 같은 트랜잭션에서 저장
        transactionRepository.saveAndFlush(
                AiCreditTransaction.freeGrant(
                        wallet,
                        INITIAL_FREE_CREDITS,
                        key
                )
        );
    }
}
