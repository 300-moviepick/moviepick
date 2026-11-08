package com.moviepick.domain.payment.client.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.moviepick.domain.payment.client.PortOneClient;
import com.moviepick.domain.payment.client.PortOnePayment;
import com.moviepick.domain.payment.client.PortOneProperties;
import com.moviepick.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PortOneWebhookReceiverTest {

    private static final String SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("receiver-test-secret".getBytes(StandardCharsets.UTF_8));
    private static final String WEBHOOK_ID = "msg_receiver_1";

    private final PortOneClient portOneClient = mock(PortOneClient.class);
    private final PortOneWebhookReceiver receiver = new PortOneWebhookReceiver(
            new PortOneWebhookVerifier(new PortOneProperties(
                    "https://api.portone.io", "test-api-secret", "test-store-id", "test-channel-key", SECRET)),
            portOneClient,
            JsonMapper.builder().build());

    @Test
    @DisplayName("Transaction.Paid 웹훅에서 이벤트 유형과 paymentId를 꺼낸다")
    void parsePaid() {
        PortOneWebhookEvent event = receiveSigned(PortOneWebhookTestSigner.paidBody("support-abc"));

        assertThat(event.webhookId()).isEqualTo(WEBHOOK_ID);
        assertThat(event.type()).isEqualTo("Transaction.Paid");
        assertThat(event.paymentId()).isEqualTo("support-abc");
        assertThat(event.isTransactionPaid()).isTrue();
    }

    @Test
    @DisplayName("알 수 없는 이벤트 유형과 필드는 오류 없이 넘긴다")
    void unknownTypeAndFields() {
        PortOneWebhookEvent event = receiveSigned("""
                {"type":"Something.New","extra":{"a":1},"data":{"storeId":"s","unknown":true}}""");

        assertThat(event.type()).isEqualTo("Something.New");
        assertThat(event.paymentId()).isNull();
        assertThat(event.isTransactionPaid()).isFalse();
    }

    @Test
    @DisplayName("서명은 맞지만 JSON이 깨졌으면 INVALID_PAYLOAD")
    void brokenJson() {
        assertInvalidPayload("{\"type\":\"Transaction.Paid\",");
    }

    @Test
    @DisplayName("결제 이벤트인데 paymentId가 없으면 INVALID_PAYLOAD")
    void transactionWithoutPaymentId() {
        assertInvalidPayload("{\"type\":\"Transaction.Paid\",\"data\":{\"storeId\":\"s\"}}");
    }

    @Test
    @DisplayName("이벤트 유형이 없으면 INVALID_PAYLOAD")
    void withoutType() {
        assertInvalidPayload("{\"data\":{\"paymentId\":\"support-abc\"}}");
    }

    @Test
    @DisplayName("서명이 틀리면 본문을 읽기 전에 INVALID_SIGNATURE")
    void invalidSignatureBeforeParsing() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> receiver.receive(WEBHOOK_ID, timestamp, "v1,AAAA", "not json".getBytes()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PortOneWebhookErrorCode.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("재조회는 기존 PortOneClient를 그대로 쓴다")
    void fetchPaymentUsesClient() {
        PortOnePayment paid = new PortOnePayment(PortOnePayment.Status.PAID, 3_000L, "KRW");
        given(portOneClient.getPayment("support-abc")).willReturn(Optional.of(paid));

        assertThat(receiver.fetchPayment("support-abc")).contains(paid);
    }

    private PortOneWebhookEvent receiveSigned(String body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, timestamp, body);
        return receiver.receive(WEBHOOK_ID, timestamp, signature, body.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalidPayload(String body) {
        assertThatThrownBy(() -> receiveSigned(body))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PortOneWebhookErrorCode.INVALID_PAYLOAD);
    }
}
