package com.moviepick.domain.support.service;

import com.moviepick.domain.support.client.PortOnePayment;
import com.moviepick.domain.support.client.webhook.PortOneWebhookEvent;
import com.moviepick.domain.support.client.webhook.PortOneWebhookReceiver;
import com.moviepick.domain.support.dto.response.SupportConfirmResponse;
import com.moviepick.domain.support.entity.Payment;
import com.moviepick.domain.support.entity.PaymentStatus;
import com.moviepick.domain.support.entity.ProductType;
import com.moviepick.domain.support.exception.SupportErrorCode;
import com.moviepick.domain.support.repository.PaymentRepository;
import com.moviepick.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 후원 결제 웹훅 처리.
 *
 * <p>PAID 반영은 결제 확인(confirm)과 같은 {@link SupportService#applyPortOneResult}를 쓴다.
 * 둘 다 PaymentRepository의 조건부 UPDATE(CREATED일 때만 변경)를 거치므로,
 * confirm과 웹훅 중 무엇이 먼저 오든, 웹훅이 여러 번 오든 한 번만 반영된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportWebhookService {

    // SupportService의 금액 검증 기준 통화와 같다 (불일치 로그의 기대 통화 표시용)
    private static final String EXPECTED_CURRENCY = "KRW";

    private final PaymentRepository paymentRepository;
    private final SupportService supportService;
    private final PortOneWebhookReceiver webhookReceiver;

    /**
     * 트랜잭션 경계는 confirm과 같다: 이 메서드에는 트랜잭션을 걸지 않는다.
     * PortOne 재조회 동안 DB 커넥션을 잡지 않고, markPaid/markFailed는 Repository 메서드 단위 트랜잭션에서
     * confirm과 똑같은 조건(WHERE status = CREATED)으로 실행·커밋된다.
     */
    public SupportWebhookResult handle(PortOneWebhookEvent event) {
        SupportWebhookResult result = process(event);
        log.info("후원 웹훅 처리 결과: webhookId={}, type={}, paymentId={}, result={}",
                event.webhookId(), event.type(), event.paymentId(), result);
        return result;
    }

    private SupportWebhookResult process(PortOneWebhookEvent event) {
        if (!event.isTransactionPaid()) {
            return SupportWebhookResult.IGNORED;
        }

        String paymentId = event.paymentId();
        Optional<Payment> found = paymentRepository.findByPaymentId(paymentId);
        if (found.isEmpty()) {
            log.warn("후원 웹훅: 존재하지 않는 결제: paymentId={}", paymentId);
            return SupportWebhookResult.NOT_FOUND;
        }

        Payment payment = found.get();
        if (payment.getProductType() != ProductType.SITE_SUPPORT) {
            log.warn("후원 웹훅: 후원 결제가 아님: paymentId={}, productType={}", paymentId, payment.getProductType());
            return SupportWebhookResult.IGNORED;
        }

        // 이미 처리가 끝난 결제는 PortOne을 다시 부르지 않는다. 상태도 바꾸지 않는다 (PAID → FAILED 불가).
        switch (payment.getStatus()) {
            case CREATED -> {
                // 아래에서 처리
            }
            case PAID -> {
                log.info("후원 웹훅: 이미 PAID 처리된 결제: paymentId={}", paymentId);
                return SupportWebhookResult.ALREADY_PROCESSED;
            }
            case FAILED, EXPIRED, CANCELLED -> {
                log.warn("후원 웹훅: PortOne 결제 완료 통지 수신 — 수동 확인 필요: paymentId={}, currentStatus={}",
                        paymentId, payment.getStatus());
                return SupportWebhookResult.ALREADY_PROCESSED;
            }
        }

        // 일시적 상황일 수 있으므로 5xx로 응답해 PortOne 재시도를 받는다
        PortOnePayment portOnePayment = webhookReceiver.fetchPayment(paymentId)
                .orElseThrow(() -> {
                    log.warn("후원 웹훅: PortOne 재조회 결과 결제 건 없음: paymentId={}", paymentId);
                    return new BusinessException(SupportErrorCode.PORTONE_REQUEST_FAILED);
                });

        if (portOnePayment.status() != PortOnePayment.Status.PAID) {
            log.info("후원 웹훅: PortOne 재조회 결과가 결제 완료가 아님: paymentId={}, portOneStatus={}",
                    paymentId, portOnePayment.status());
            return SupportWebhookResult.NOT_PAID;
        }

        SupportConfirmResponse applied = supportService.applyPortOneResult(payment, portOnePayment);
        return switch (applied.status()) {
            case PAID -> SupportWebhookResult.PAID_APPLIED;
            case FAILED -> {
                log.warn("후원 웹훅 결제 금액 불일치: paymentId={}, expectedAmount={}, expectedCurrency={}, "
                                + "actualAmount={}, actualCurrency={}",
                        paymentId, payment.getAmount(), EXPECTED_CURRENCY,
                        portOnePayment.totalAmount(), portOnePayment.currency());
                yield SupportWebhookResult.AMOUNT_MISMATCH;
            }
            case CREATED, EXPIRED, CANCELLED -> SupportWebhookResult.ALREADY_PROCESSED;
        };
    }
}
