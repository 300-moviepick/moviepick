package com.moviepick.domain.support.service;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.payment.client.PortOneClient;
import com.moviepick.domain.payment.client.PortOnePayment;
import com.moviepick.domain.payment.client.PortOneProperties;
import com.moviepick.domain.support.dto.request.SupportCreateRequest;
import com.moviepick.domain.payment.dto.response.ConfirmResult;
import com.moviepick.domain.support.dto.response.SupportConfirmResponse;
import com.moviepick.domain.support.dto.response.SupportCreateResponse;
import com.moviepick.domain.payment.entity.Payment;
import com.moviepick.domain.payment.entity.PaymentStatus;
import com.moviepick.domain.payment.entity.ProductType;
import com.moviepick.domain.support.exception.SupportErrorCode;
import com.moviepick.domain.payment.repository.PaymentRepository;
import com.moviepick.domain.payment.service.port.PaidPaymentHandler;
import com.moviepick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 후원 (개발자를 위한 커피 한잔).
 *
 * <p>결제 확인(confirm)은 외부 API(PortOne)를 호출하므로 클래스 전체에 트랜잭션을 걸지 않는다.
 * 조회 중에 DB 커넥션을 잡지 않도록 하고, 상태 변경은 Repository의 조건부 UPDATE가 각자 트랜잭션으로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService implements PaidPaymentHandler {

    // 허용 금액과 주문명. 이 외의 금액은 거절한다.
    private static final Map<Long, String> ORDER_NAMES = Map.of(
            3_000L, "MoviePick 개발자 후원 - 커피 1잔",
            6_000L, "MoviePick 개발자 후원 - 커피 2잔",
            9_000L, "MoviePick 개발자 후원 - 커피 3잔");

    private final PaymentRepository paymentRepository;
    private final MemberService memberService;
    private final PortOneClient portOneClient;
    private final PortOneProperties portOneProperties;

    // ===================== 후원 생성 =====================

    @Transactional
    public SupportCreateResponse createSupport(Long memberId, SupportCreateRequest request) {
        String orderName = ORDER_NAMES.get(request.amount());
        if (orderName == null) {
            throw new BusinessException(SupportErrorCode.INVALID_SUPPORT_AMOUNT);
        }

        Member member = memberService.getActiveMember(memberId);
        String paymentId = "support-" + UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.createSupport(member, request.amount(), paymentId));

        return SupportCreateResponse.of(payment, orderName,
                portOneProperties.storeId(), portOneProperties.channelKey());
    }

    // ===================== 결제 확인 =====================

    /**
     * 브라우저가 보낸 결제 결과·금액은 믿지 않고, PortOne에 직접 조회해서 판단한다.
     */
    public SupportConfirmResponse confirm(Long memberId, String paymentId) {
        // 후원 결제만 다룬다. AI 크레딧 결제를 여기서 PAID로 바꾸면 크레딧이 지급되지 않으므로 없는 결제로 본다.
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .filter(found -> found.getProductType() == ProductType.SITE_SUPPORT)
                .orElseThrow(() -> new BusinessException(SupportErrorCode.SUPPORT_NOT_FOUND));
        if (!payment.isOwnedBy(memberId)) {
            throw new BusinessException(SupportErrorCode.NOT_SUPPORT_OWNER);
        }

        // 이미 처리가 끝난 주문은 PortOne을 다시 부르지 않는다.
        if (payment.getStatus() != PaymentStatus.CREATED) {
            return respondByStoredStatus(payment);
        }

        return portOneClient.getPayment(paymentId)
                .map(portOnePayment -> applyPortOneResult(payment, portOnePayment))
                .orElseGet(() -> pending(payment));   // PortOne에 결제 건이 아직 없음
    }

    // ===================== 웹훅 (PaidPaymentHandler) =====================

    @Override
    public ProductType productType() {
        return ProductType.SITE_SUPPORT;
    }

    @Override
    public PaymentStatus applyPaid(Payment payment, PortOnePayment portOnePayment) {
        return applyPortOneResult(payment, portOnePayment).status();
    }

    SupportConfirmResponse applyPortOneResult(Payment payment, PortOnePayment portOnePayment) {
        String paymentId = payment.getPaymentId();

        return switch (portOnePayment.status()) {
            case PAID -> {
                if (payment.matchesPaidAmount(portOnePayment.totalAmount(), portOnePayment.currency())) {
                    yield markPaid(paymentId);
                }
                log.warn("후원 결제 금액·통화 불일치: paymentId={}, expectedAmount={}, expectedCurrency={}, "
                                + "actualAmount={}, actualCurrency={}",
                        paymentId, payment.getAmount(), Payment.CURRENCY_KRW,
                        portOnePayment.totalAmount(), portOnePayment.currency());
                yield markFailed(paymentId);
            }
            // 같은 paymentId로 재시도할 수 있으므로 상태는 CREATED로 둔다.
            case FAILED -> SupportConfirmResponse.of(paymentId, PaymentStatus.CREATED, ConfirmResult.FAILED);
            case CANCELLED, PARTIAL_CANCELLED -> {
                log.warn("PortOne에서 취소된 결제가 CREATED 상태로 남아 있음: paymentId={}, portOneStatus={}",
                        paymentId, portOnePayment.status());
                yield SupportConfirmResponse.of(paymentId, PaymentStatus.CREATED, ConfirmResult.FAILED);
            }
            case READY, PAY_PENDING, VIRTUAL_ACCOUNT_ISSUED -> pending(payment);
            case UNKNOWN -> {
                log.warn("알 수 없는 PortOne 결제 상태: paymentId={}", paymentId);
                yield pending(payment);
            }
        };
    }

    private SupportConfirmResponse markPaid(String paymentId) {
        if (paymentRepository.markPaid(paymentId, LocalDateTime.now()) == 1) {
            return SupportConfirmResponse.of(paymentId, PaymentStatus.PAID, ConfirmResult.COMPLETED);
        }
        return respondByLatestStatus(paymentId);
    }

    private SupportConfirmResponse markFailed(String paymentId) {
        if (paymentRepository.markFailed(paymentId) == 1) {
            return SupportConfirmResponse.of(paymentId, PaymentStatus.FAILED, ConfirmResult.FAILED);
        }
        return respondByLatestStatus(paymentId);
    }

    /** 조건부 UPDATE가 0행이면 다른 요청(웹훅 등)이 먼저 처리한 것이므로, 최신 상태를 다시 읽어 응답한다. */
    private SupportConfirmResponse respondByLatestStatus(String paymentId) {
        Payment latest = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(SupportErrorCode.SUPPORT_NOT_FOUND));
        if (latest.getStatus() == PaymentStatus.CREATED) {
            return pending(latest);
        }
        return respondByStoredStatus(latest);
    }

    private SupportConfirmResponse respondByStoredStatus(Payment payment) {
        ConfirmResult result = payment.getStatus() == PaymentStatus.PAID
                ? ConfirmResult.COMPLETED
                : ConfirmResult.FAILED;   // FAILED, EXPIRED, CANCELLED
        return SupportConfirmResponse.of(payment.getPaymentId(), payment.getStatus(), result);
    }

    private SupportConfirmResponse pending(Payment payment) {
        return SupportConfirmResponse.of(payment.getPaymentId(), payment.getStatus(), ConfirmResult.PENDING);
    }
}
