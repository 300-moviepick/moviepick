package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// 지갑이 없는 회원(AI 기능 이전 가입자, 시드 계정)에게 가입 때와 같은 무료 크레딧 지갑을 만든다.
// 호출하는 쪽은 트랜잭션 밖에서 부른다. 같은 트랜잭션 안의 잠금 조회보다 먼저 지갑을 만들어 둔다.
@Slf4j
@Service
public class AiCreditWalletProvisioner {

    private final AiCreditWalletRepository walletRepository;
    private final MemberRepository memberRepository;
    private final AiSignupCreditService signupCreditService;
    private final TransactionTemplate newTransaction;

    public AiCreditWalletProvisioner(
            AiCreditWalletRepository walletRepository,
            MemberRepository memberRepository,
            AiSignupCreditService signupCreditService,
            PlatformTransactionManager transactionManager
    ) {
        this.walletRepository = walletRepository;
        this.memberRepository = memberRepository;
        this.signupCreditService = signupCreditService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public void ensureWallet(Long memberId) {

        if (walletRepository.findByMember_Id(memberId).isPresent()) {
            return;
        }

        try {
            // 지갑 생성 + 무료 지급 + FREE_GRANT 원장을 한 트랜잭션으로 (가입 지급과 같은 로직)
            newTransaction.executeWithoutResult(status -> {
                Member member = memberRepository.getReferenceById(memberId);
                signupCreditService.grantTo(member);
            });

            log.info("AI 크레딧 지갑 생성. memberId={}", memberId);

        } catch (DataAccessException e) {

            // 동시 요청이 먼저 만들었으면 유니크 제약에 걸린다. 지갑이 있으면 정상으로 본다
            if (walletRepository.findByMember_Id(memberId).isPresent()) {
                return;
            }

            throw e;
        }
    }
}
