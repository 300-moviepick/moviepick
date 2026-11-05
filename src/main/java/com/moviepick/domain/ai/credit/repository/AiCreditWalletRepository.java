package com.moviepick.domain.ai.credit.repository;

import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCreditWalletRepository
        extends JpaRepository<AiCreditWallet, Long> {

    Optional<AiCreditWallet> findByMember_Id(Long memberId);

    // [추가] 크레딧 사용 시 지갑에 쓰기 잠금을 건다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w
            FROM AiCreditWallet w
            WHERE w.member.id = :memberId
            """)
    Optional<AiCreditWallet> findByMemberIdForUpdate(
            @Param("memberId") Long memberId
    );
}
