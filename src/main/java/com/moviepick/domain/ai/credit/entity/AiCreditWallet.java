package com.moviepick.domain.ai.credit.entity;

import com.moviepick.domain.ai.exception.AiErrorCode;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "ai_credit_wallets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_credit_wallets_member",
                columnNames = "member_id"
        )
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCreditWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원당 하나의 AI 지갑
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "free_available", nullable = false)
    private int freeAvailable;

    @Column(name = "paid_available", nullable = false)
    private int paidAvailable;

    // 동시 잔액 변경 감지
    @Version
    @Column(nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AiCreditWallet(Member member) {
        this.member = Objects.requireNonNull(member);
        this.freeAvailable = 0;
        this.paidAvailable = 0;
    }

    // 무료 크레딧 지급
    public void grantFreeCredits(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "지급 수량은 1 이상이어야 합니다."
            );
        }

        this.freeAvailable = Math.addExact(
                this.freeAvailable, amount
        );
    }

    // 유료 크레딧 충전 (결제 완료 후)
    public void grantPaidCredits(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "충전 수량은 1 이상이어야 합니다."
            );
        }

        this.paidAvailable = Math.addExact(
                this.paidAvailable, amount
        );
    }

    // 무료 크레딧을 우선 사용하고, 없으면 유료 크레딧을 사용한다.
    public AiCreditBucket useOneCredit() {

        if (freeAvailable > 0) {
            freeAvailable--;
            return AiCreditBucket.FREE;
        }

        if (paidAvailable > 0) {
            paidAvailable--;
            return AiCreditBucket.PAID;
        }

        throw new BusinessException(
                AiErrorCode.AI_CREDIT_INSUFFICIENT
        );
    }

    // 사용한 크레딧 종류에 맞춰 1회 복구
    public void restoreOneCredit(AiCreditBucket bucket) {

        if (bucket == null) {
            throw new IllegalArgumentException("크레딧 종류가 필요합니다.");
        }

        switch (bucket) {
            case FREE ->
                    freeAvailable = Math.addExact(freeAvailable, 1);
            case PAID ->
                    paidAvailable = Math.addExact(paidAvailable, 1);
        }
    }
}