package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.dto.request.AiCreditPurchaseRequest;
import com.moviepick.domain.ai.credit.dto.response.AiCreditProductResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditPurchaseConfirmResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditPurchaseCreateResponse;
import com.moviepick.domain.ai.credit.entity.AiCreditProduct;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.support.client.PortOneClient;
import com.moviepick.domain.support.client.PortOnePayment;
import com.moviepick.domain.support.client.PortOneProperties;
import com.moviepick.domain.support.dto.response.ConfirmResult;
import com.moviepick.domain.support.entity.Payment;
import com.moviepick.domain.support.entity.PaymentStatus;
import com.moviepick.domain.support.entity.ProductType;
import com.moviepick.domain.support.repository.PaymentRepository;
import com.moviepick.domain.support.service.port.PaidPaymentHandler;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * AI 크레딧 구매 (PortOne 결제).
 *
 * <p>흐름은 후원과 같다: 생성(CREATED) → 결제창 → confirm 또는 웹훅으로 PortOne 재조회 → 지급.
 * confirm과 웹훅은 같은 {@link #applyPortOneResult}와 {@link AiCreditPurchaseGrantService}를 쓴다.
 * 이 서비스는 AI 스위치(spring.ai.model.chat)와 관계없이 항상 등록된다. 돈을 낸 결제는 반드시 지급한다.
 * PortOne 호출 동안 DB 커넥션을 잡지 않도록 confirm에는 트랜잭션을 걸지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCreditPurchaseService implements PaidPaymentHandler {

    private static final String CURRENCY_KRW = "KRW";
    private static final String PAYMENT_ID_PREFIX = "credit-";

    private final PaymentRepository paymentRepository;
    private final MemberService memberService;
    private final PortOneClient portOneClient;
    private final PortOneProperties portOneProperties;
    private final AiCreditWalletProvisioner walletProvisioner;
    private final AiCreditPurchaseGrantService grantService;
    private final AiCreditQueryService queryService;

    // ===================== 상품 목록 =====================

    public List<AiCreditProductResponse> getProducts() {
        return Arrays.stream(AiCreditProduct.values())
                .map(AiCreditProductResponse::from)
                .toList();
    }

    // ===================== 구매 생성 =====================

    @Transactional
    public AiCreditPurchaseCreateResponse createPurchase(
            Long memberId,
            AiCreditPurchaseRequest request
    ) {
        AiCreditProduct product = AiCreditProduct.fromCode(request.productCode())
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.AI_CREDIT_PRODUCT_NOT_FOUND
                ));

        Member member = memberService.getActiveMember(memberId);
        String paymentId = PAYMENT_ID_PREFIX + UUID.randomUUID();

        Payment payment = paymentRepository.save(
                Payment.createCredit(member, product.getAmount(), paymentId)
        );

        return AiCreditPurchaseCreateResponse.of(
                payment,
                product.getOrderName(),
                portOneProperties.storeId(),
                portOneProperties.channelKey()
        );
    }

    // ===================== 구매 확정 =====================

    /** 브라우저가 보낸 결제 결과·금액은 믿지 않고, PortOne에 직접 조회해서 판단한다. */
    public AiCreditPurchaseConfirmResponse confirm(Long memberId, String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .filter(found -> found.getProductType() == ProductType.AI_CREDIT)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.AI_CREDIT_PURCHASE_NOT_FOUND
                ));

        if (!payment.isOwnedBy(memberId)) {
            throw new BusinessException(CommonErrorCode.NOT_AI_CREDIT_PURCHASE_OWNER);
        }

        // 잔액 응답과 지급에 필요하다. 지급 트랜잭션보다 먼저 만든다
        walletProvisioner.ensureWallet(memberId);

        Outcome outcome;

        // 이미 처리가 끝난 주문은 PortOne을 다시 부르지 않는다.
        if (payment.getStatus() != PaymentStatus.CREATED) {
            outcome = byStoredStatus(payment.getStatus());
        } else {
            outcome = portOneClient.getPayment(paymentId)
                    .map(portOnePayment -> applyPortOneResult(payment, portOnePayment))
                    .orElseGet(() -> Outcome.pending(payment.getStatus()));
        }

        return AiCreditPurchaseConfirmResponse.of(
                paymentId,
                outcome.status(),
                outcome.result(),
                queryService.getBalance(memberId)
        );
    }

    // ===================== 웹훅 (PaidPaymentHandler) =====================

    @Override
    public ProductType productType() {
        return ProductType.AI_CREDIT;
    }

    @Override
    public PaymentStatus applyPaid(Payment payment, PortOnePayment portOnePayment) {
        walletProvisioner.ensureWallet(payment.getMember().getId());
        return applyPortOneResult(payment, portOnePayment).status();
    }

    // ===================== confirm·웹훅 공통 =====================

    Outcome applyPortOneResult(Payment payment, PortOnePayment portOnePayment) {
        String paymentId = payment.getPaymentId();

        return switch (portOnePayment.status()) {
            case PAID -> {
                if (isAmountMatched(payment, portOnePayment)) {
                    yield grant(payment);
                }
                log.warn("크레딧 결제 금액 불일치: paymentId={}, expectedAmount={}, actualAmount={}, currency={}",
                        paymentId, payment.getAmount(), portOnePayment.totalAmount(), portOnePayment.currency());
                yield markFailed(paymentId);
            }
            // 같은 paymentId로 재시도할 수 있으므로 상태는 CREATED로 둔다.
            case FAILED -> new Outcome(PaymentStatus.CREATED, ConfirmResult.FAILED);
            case CANCELLED, PARTIAL_CANCELLED -> {
                log.warn("PortOne에서 취소된 크레딧 결제가 CREATED 상태로 남아 있음: paymentId={}, portOneStatus={}",
                        paymentId, portOnePayment.status());
                yield new Outcome(PaymentStatus.CREATED, ConfirmResult.FAILED);
            }
            case READY, PAY_PENDING, VIRTUAL_ACCOUNT_ISSUED -> Outcome.pending(payment.getStatus());
            case UNKNOWN -> {
                log.warn("알 수 없는 PortOne 결제 상태: paymentId={}", paymentId);
                yield Outcome.pending(payment.getStatus());
            }
        };
    }

    private Outcome grant(Payment payment) {
        // 결제 건에 상품 코드가 없으므로 금액으로 상품을 찾는다 (가격은 상품마다 다르다)
        AiCreditProduct product = AiCreditProduct.fromAmount(payment.getAmount())
                .orElseThrow(() -> {
                    log.error("결제 금액에 맞는 크레딧 상품 없음 — 수동 확인 필요: paymentId={}, amount={}",
                            payment.getPaymentId(), payment.getAmount());
                    return new BusinessException(CommonErrorCode.AI_CREDIT_PRICE_UNMAPPED);
                });

        boolean granted = grantService.markPaidAndGrant(
                payment.getPaymentId(),
                payment.getMember().getId(),
                product.getCredits()
        );

        if (granted) {
            log.info("AI 크레딧 지급: paymentId={}, credits={}", payment.getPaymentId(), product.getCredits());
            return new Outcome(PaymentStatus.PAID, ConfirmResult.COMPLETED);
        }
        return byLatestStatus(payment.getPaymentId());
    }

    private Outcome markFailed(String paymentId) {
        if (paymentRepository.markFailed(paymentId) == 1) {
            return new Outcome(PaymentStatus.FAILED, ConfirmResult.FAILED);
        }
        return byLatestStatus(paymentId);
    }

    /** 조건부 UPDATE가 0행이면 다른 요청(웹훅 등)이 먼저 처리한 것이므로, 최신 상태를 다시 읽어 응답한다. */
    private Outcome byLatestStatus(String paymentId) {
        Payment latest = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AI_CREDIT_PURCHASE_NOT_FOUND));
        if (latest.getStatus() == PaymentStatus.CREATED) {
            return Outcome.pending(PaymentStatus.CREATED);
        }
        return byStoredStatus(latest.getStatus());
    }

    private Outcome byStoredStatus(PaymentStatus status) {
        ConfirmResult result = status == PaymentStatus.PAID
                ? ConfirmResult.COMPLETED
                : ConfirmResult.FAILED;   // FAILED, EXPIRED, CANCELLED
        return new Outcome(status, result);
    }

    private boolean isAmountMatched(Payment payment, PortOnePayment portOnePayment) {
        return portOnePayment.totalAmount() != null
                && portOnePayment.totalAmount() == payment.getAmount()
                && CURRENCY_KRW.equals(portOnePayment.currency());
    }

    record Outcome(PaymentStatus status, ConfirmResult result) {

        static Outcome pending(PaymentStatus status) {
            return new Outcome(status, ConfirmResult.PENDING);
        }
    }
}
