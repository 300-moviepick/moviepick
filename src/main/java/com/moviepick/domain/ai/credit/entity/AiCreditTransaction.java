package com.moviepick.domain.ai.credit.entity;

import com.moviepick.domain.support.entity.Payment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "ai_credit_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_credit_tx_idempotency",
                columnNames = "idempotency_key"
        ),
        indexes = {
                @Index(
                        name = "idx_ai_credit_tx_wallet_created",
                        columnList = "wallet_id, created_at"
                ),
                @Index(
                        name = "idx_ai_credit_tx_wallet_request",
                        columnList = "wallet_id, request_key"
                ),
                @Index(
                        name = "idx_ai_credit_tx_event_created",
                        columnList = "event_type, created_at"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private AiCreditWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_key", length = 36, columnDefinition = "char(36)")
    private String requestKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 24)
    private AiCreditEventType eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private AiCreditBucket bucket;

    @Column(nullable = false)
    private int delta;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private AiCreditTransaction(
            AiCreditWallet wallet,
            Payment payment,
            String requestKey,
            AiCreditEventType eventType,
            AiCreditBucket bucket,
            int delta,
            String idempotencyKey
    ) {
        this.wallet = Objects.requireNonNull(wallet);
        this.payment = payment;
        this.requestKey = requestKey;
        this.eventType = Objects.requireNonNull(eventType);
        this.bucket = Objects.requireNonNull(bucket);
        this.delta = delta;

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 150) {
            throw new IllegalArgumentException("유효한 멱등 키가 필요합니다.");
        }

        if (requestKey != null && requestKey.length() != 36) {
            throw new IllegalArgumentException("요청 키는 UUID 형식이어야 합니다.");
        }

        this.idempotencyKey = idempotencyKey;
    }

    // 최초 무료 크레딧 지급 기록
    public static AiCreditTransaction freeGrant(
            AiCreditWallet wallet,
            int amount,
            String key
    ) {
        requirePositive(amount);

        return new AiCreditTransaction(
                wallet, null, null,
                AiCreditEventType.FREE_GRANT,
                AiCreditBucket.FREE, amount, key
        );
    }

    // 결제 완료 후 충전 기록
    public static AiCreditTransaction purchase(
            AiCreditWallet wallet,
            Payment payment,
            int amount,
            String key
    ) {
        requirePositive(amount);

        return new AiCreditTransaction(
                wallet, Objects.requireNonNull(payment), null,
                AiCreditEventType.PURCHASE,
                AiCreditBucket.PAID, amount, key
        );
    }

    // 추천 요청 선차감 기록
    public static AiCreditTransaction use(
            AiCreditWallet wallet,
            String requestKey,
            AiCreditBucket bucket,
            String key
    ) {
        requireRequestKey(requestKey);

        return new AiCreditTransaction(
                wallet, null, requestKey,
                AiCreditEventType.USE,
                bucket, -1, key
        );
    }

    // 추천 완료 기록
    public static AiCreditTransaction complete(
            AiCreditWallet wallet,
            String requestKey,
            AiCreditBucket bucket,
            String key
    ) {
        requireRequestKey(requestKey);

        return new AiCreditTransaction(
                wallet, null, requestKey,
                AiCreditEventType.COMPLETE,
                bucket, 0, key
        );
    }

    // 추천 실패 복구 기록
    public static AiCreditTransaction restore(
            AiCreditWallet wallet,
            String requestKey,
            AiCreditBucket bucket,
            String key
    ) {
        requireRequestKey(requestKey);

        return new AiCreditTransaction(
                wallet, null, requestKey,
                AiCreditEventType.RESTORE,
                bucket, 1, key
        );
    }

    private static void requirePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("크레딧 수량은 1 이상이어야 합니다.");
        }
    }

    private static void requireRequestKey(String requestKey) {
        if (requestKey == null || requestKey.length() != 36) {
            throw new IllegalArgumentException("유효한 요청 UUID가 필요합니다.");
        }
    }

    // AI 추천 크레딧 1회 사용 원장 생성
    public static AiCreditTransaction use(
            AiCreditWallet wallet,
            AiCreditBucket bucket,
            String requestKey,
            String idempotencyKey
    ) {
        AiCreditTransaction transaction = new AiCreditTransaction();

        transaction.wallet = wallet;
        transaction.requestKey = requestKey;
        transaction.eventType = AiCreditEventType.USE;
        transaction.bucket = bucket;
        transaction.delta = -1;
        transaction.idempotencyKey = idempotencyKey;

        return transaction;
    }

    // [추가] 기존 USE 거래를 기준으로 복구 원장 생성
    public static AiCreditTransaction restore(
            AiCreditTransaction originalUse,
            String idempotencyKey
    ) {
        if (originalUse.getEventType() != AiCreditEventType.USE) {
            throw new IllegalArgumentException("USE 거래만 복구할 수 있습니다.");
        }

        AiCreditTransaction transaction = new AiCreditTransaction();

        transaction.wallet = originalUse.getWallet();
        transaction.requestKey = originalUse.getRequestKey();
        transaction.eventType = AiCreditEventType.RESTORE;
        transaction.bucket = originalUse.getBucket();
        transaction.delta = 1;
        transaction.idempotencyKey = idempotencyKey;

        return transaction;
    }

    // [추가] AI 추천 성공 확정 거래 생성
    public static AiCreditTransaction complete(
            AiCreditTransaction originalUse,
            String idempotencyKey
    ) {
        if (originalUse.getEventType() != AiCreditEventType.USE) {
            throw new IllegalArgumentException("USE 거래만 완료할 수 있습니다.");
        }

        AiCreditTransaction transaction = new AiCreditTransaction();

        transaction.wallet = originalUse.getWallet();
        transaction.requestKey = originalUse.getRequestKey();
        transaction.eventType = AiCreditEventType.COMPLETE;
        transaction.bucket = originalUse.getBucket();
        transaction.delta = 0;
        transaction.idempotencyKey = idempotencyKey;

        return transaction;
    }
}