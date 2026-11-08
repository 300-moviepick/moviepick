package com.moviepick.domain.payment.client.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviepick.domain.payment.client.PortOneClient;
import com.moviepick.domain.payment.client.PortOnePayment;
import com.moviepick.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * PortOne 웹훅 공통 처리 (결제 유형과 무관).
 *
 * <p>결제 유형별 웹훅 엔드포인트는 이 컴포넌트로 ① 서명 검증과 본문 파싱({@link #receive}),
 * ② PortOne 결제 재조회({@link #fetchPayment})를 하고, 상태 반영만 각자 한다.
 * 웹훅 본문의 결제 상태·금액은 쓰지 않는다. 반드시 재조회 결과로 판단한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneWebhookReceiver {

    private final PortOneWebhookVerifier verifier;
    private final PortOneClient portOneClient;
    private final ObjectMapper objectMapper;

    /**
     * 서명을 검증한 뒤 이벤트 유형과 paymentId를 꺼낸다.
     *
     * @throws BusinessException INVALID_SIGNATURE(서명 검증 실패), INVALID_PAYLOAD(서명은 맞지만 본문을 읽을 수 없음)
     */
    public PortOneWebhookEvent receive(String webhookId, String webhookTimestamp, String signatureHeader,
            byte[] rawBody) {
        verifier.verify(webhookId, webhookTimestamp, signatureHeader, rawBody);

        WebhookBody body;
        try {
            body = objectMapper.readValue(rawBody, WebhookBody.class);
        } catch (JacksonException e) {
            log.warn("PortOne 웹훅 본문 파싱 실패: webhookId={}, cause={}",
                    PortOneWebhookVerifier.safe(webhookId), e.getClass().getSimpleName());
            throw new BusinessException(PortOneWebhookErrorCode.INVALID_PAYLOAD);
        }
        if (body == null || body.type() == null || body.type().isBlank()) {
            log.warn("PortOne 웹훅 이벤트 유형 없음: webhookId={}", PortOneWebhookVerifier.safe(webhookId));
            throw new BusinessException(PortOneWebhookErrorCode.INVALID_PAYLOAD);
        }

        String paymentId = body.data() == null ? null : body.data().paymentId();
        if (body.type().startsWith(PortOneWebhookEvent.TRANSACTION_PREFIX) && (paymentId == null || paymentId.isBlank())) {
            log.warn("PortOne 결제 웹훅에 paymentId 없음: webhookId={}, type={}",
                    PortOneWebhookVerifier.safe(webhookId), PortOneWebhookVerifier.safe(body.type()));
            throw new BusinessException(PortOneWebhookErrorCode.INVALID_PAYLOAD);
        }

        log.info("PortOne 웹훅 수신: webhookId={}, type={}, paymentId={}",
                PortOneWebhookVerifier.safe(webhookId), PortOneWebhookVerifier.safe(body.type()),
                PortOneWebhookVerifier.safe(paymentId));
        return new PortOneWebhookEvent(webhookId, body.type(), paymentId);
    }

    /**
     * paymentId로 PortOne에 결제를 다시 조회한다. 기존 결제 확인(confirm)과 같은 클라이언트를 쓴다.
     *
     * @return PortOne에 결제 건이 없으면 empty
     * @throws BusinessException PORTONE_REQUEST_FAILED, PORTONE_TIMEOUT (일시적 오류 → 5xx 응답으로 PortOne 재시도 유도)
     */
    public Optional<PortOnePayment> fetchPayment(String paymentId) {
        return portOneClient.getPayment(paymentId);
    }

    /** 웹훅 본문 중 필요한 값만 받는다. 알 수 없는 필드와 이벤트 유형은 무시한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebhookBody(String type, Data data) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Data(String paymentId) {
        }
    }
}
