package com.moviepick.domain.payment.repository;

import com.moviepick.domain.payment.entity.Payment;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    // ===== 상태 변경: 조건부 UPDATE (코드 컨벤션 3.8) =====
    // CREATED일 때만 바뀐다. 반환값(바뀐 행 수)이 0이면 이미 다른 요청이 처리한 것.
    // 결제 확인 API와 웹훅이 동시에 와도 한 번만 바뀐다.
    // 서비스가 PortOne 호출 동안 트랜잭션을 잡지 않으므로, 메서드 단위로 트랜잭션을 연다.

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Payment p
               set p.status = com.moviepick.domain.payment.entity.PaymentStatus.PAID, p.paidAt = :now
             where p.paymentId = :paymentId
               and p.status = com.moviepick.domain.payment.entity.PaymentStatus.CREATED
            """)
    int markPaid(@Param("paymentId") String paymentId, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Payment p
               set p.status = com.moviepick.domain.payment.entity.PaymentStatus.FAILED
             where p.paymentId = :paymentId
               and p.status = com.moviepick.domain.payment.entity.PaymentStatus.CREATED
            """)
    int markFailed(@Param("paymentId") String paymentId);
}
