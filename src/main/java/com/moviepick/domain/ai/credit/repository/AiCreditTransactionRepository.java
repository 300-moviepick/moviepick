
package com.moviepick.domain.ai.credit.repository;

import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiCreditTransactionRepository
        extends JpaRepository<AiCreditTransaction, Long> {

    // 동일 지급 이력 확인
    boolean existsByIdempotencyKey(String idempotencyKey);

    // 멱등 키로 거래 조회
    Optional<AiCreditTransaction> findByIdempotencyKey(
            String idempotencyKey
    );

    // [추가] 특정 지갑의 거래 내역 페이징 조회
    @EntityGraph(attributePaths = "payment")
    Page<AiCreditTransaction> findByWallet_Id(
            Long walletId,
            Pageable pageable
    );
}
