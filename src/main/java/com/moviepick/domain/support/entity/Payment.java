package com.moviepick.domain.support.entity;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_payment_id",
                columnNames = "payment_id"),
        indexes = {
                @Index(name = "idx_payments_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_payments_member_id_created_at", columnList = "member_id, created_at")
        })
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(nullable = false)
    private long amount;

    // 서버가 생성해 PortOne 결제창에 넘기는 결제 ID
    @Column(name = "payment_id", nullable = false, length = 64)
    private String paymentId;

    // 상태 변경은 PaymentRepository의 조건부 UPDATE로만 한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public static Payment createSupport(Member member, long amount, String paymentId) {
        Payment payment = new Payment();
        payment.member = member;
        payment.productType = ProductType.SITE_SUPPORT;
        payment.amount = amount;
        payment.paymentId = paymentId;
        payment.status = PaymentStatus.CREATED;
        return payment;
    }

    // AI 크레딧 구매. 금액은 서버 가격표(AiCreditProduct) 값만 넘긴다
    public static Payment createCredit(Member member, long amount, String paymentId) {
        Payment payment = new Payment();
        payment.member = member;
        payment.productType = ProductType.AI_CREDIT;
        payment.amount = amount;
        payment.paymentId = paymentId;
        payment.status = PaymentStatus.CREATED;
        return payment;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }
}
