package com.moviepick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.payment.entity.Payment;
import com.moviepick.domain.payment.entity.PaymentStatus;
import com.moviepick.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/** 조건부 UPDATE가 실제 쿼리로 한 번만 적용되는지 H2로 확인한다. */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class PaymentRepositoryTest {

    private static final String PAYMENT_ID = "support-repository-test";

    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        Member member = entityManager.persist(Member.createLocal("repo@test.com", "encoded", "repo"));
        entityManager.persist(Payment.createSupport(member, 3_000L, PAYMENT_ID));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("markPaid를 두 번 호출하면 첫 번째만 1행이 바뀌고 두 번째는 0행이다")
    void markPaidOnlyOnce() {
        LocalDateTime firstPaidAt = LocalDateTime.of(2026, 10, 21, 12, 0);
        LocalDateTime secondPaidAt = firstPaidAt.plusMinutes(5);

        int first = paymentRepository.markPaid(PAYMENT_ID, firstPaidAt);
        int second = paymentRepository.markPaid(PAYMENT_ID, secondPaidAt);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);

        Payment payment = paymentRepository.findByPaymentId(PAYMENT_ID).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(firstPaidAt);
    }

    @Test
    @DisplayName("PAID가 된 뒤에는 markFailed가 0행이고 상태가 바뀌지 않는다")
    void markFailedAfterPaid() {
        paymentRepository.markPaid(PAYMENT_ID, LocalDateTime.of(2026, 10, 21, 12, 0));

        int failed = paymentRepository.markFailed(PAYMENT_ID);

        assertThat(failed).isEqualTo(0);
        assertThat(paymentRepository.findByPaymentId(PAYMENT_ID).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("CREATED에서 markFailed는 1행이 바뀐다")
    void markFailedFromCreated() {
        int failed = paymentRepository.markFailed(PAYMENT_ID);

        assertThat(failed).isEqualTo(1);
        assertThat(paymentRepository.findByPaymentId(PAYMENT_ID).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
    }
}
