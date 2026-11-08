package com.moviepick.domain.payment.client.webhook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.payment.client.PortOneProperties;
import com.moviepick.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PortOneWebhookVerifierTest {

    private static final String SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("verifier-test-secret".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("another-secret".getBytes(StandardCharsets.UTF_8));
    private static final String WEBHOOK_ID = "msg_test_1";
    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final String TIMESTAMP = String.valueOf(NOW.getEpochSecond());
    private static final String BODY = PortOneWebhookTestSigner.paidBody("support-verifier");

    private final PortOneWebhookVerifier verifier = verifierWith(SECRET);

    @Test
    @DisplayName("올바른 서명은 통과한다")
    void validSignature() {
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

        assertThatCode(() -> verify(verifier, WEBHOOK_ID, TIMESTAMP, signature, BODY, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용 범위 경계(5분)의 timestamp는 통과한다")
    void timestampAtBoundary() {
        String timestamp = String.valueOf(NOW.minus(PortOneWebhookVerifier.TOLERANCE).getEpochSecond());
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, timestamp, BODY);

        assertThatCode(() -> verify(verifier, WEBHOOK_ID, timestamp, signature, BODY, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서명 목록 중 하나만 맞아도 통과한다 (시크릿 교체 기간)")
    void oneOfMultipleSignatures() {
        String wrong = PortOneWebhookTestSigner.sign(OTHER_SECRET, WEBHOOK_ID, TIMESTAMP, BODY);
        String right = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

        assertThatCode(() -> verify(verifier, WEBHOOK_ID, TIMESTAMP, wrong + " " + right, BODY, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("위조 서명은 거부한다")
    void forgedSignature() {
        String forged = "v1," + Base64.getEncoder().encodeToString(new byte[32]);

        assertRejected(() -> verify(verifier, WEBHOOK_ID, TIMESTAMP, forged, BODY, NOW));
    }

    @Test
    @DisplayName("다른 시크릿으로 만든 서명은 거부한다")
    void signatureWithOtherSecret() {
        String signature = PortOneWebhookTestSigner.sign(OTHER_SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

        assertRejected(() -> verify(verifier, WEBHOOK_ID, TIMESTAMP, signature, BODY, NOW));
    }

    @Test
    @DisplayName("본문이 1바이트라도 바뀌면 거부한다")
    void tamperedBody() {
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);
        String tampered = BODY.replace("Transaction.Paid", "Transaction.Paie");

        assertRejected(() -> verify(verifier, WEBHOOK_ID, TIMESTAMP, signature, tampered, NOW));
    }

    @Test
    @DisplayName("다른 webhook-id로 서명을 재사용하면 거부한다")
    void signatureReusedWithOtherId() {
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

        assertRejected(() -> verify(verifier, "msg_other", TIMESTAMP, signature, BODY, NOW));
    }

    @ParameterizedTest(name = "현재 시각 기준 {0}초 차이")
    @ValueSource(longs = {-301, 301, -3600, 86400})
    @DisplayName("timestamp가 허용 범위(5분)를 넘으면 과거·미래 모두 거부한다")
    void expiredTimestamp(long offsetSeconds) {
        String timestamp = String.valueOf(NOW.getEpochSecond() + offsetSeconds);
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, timestamp, BODY);

        assertRejected(() -> verify(verifier, WEBHOOK_ID, timestamp, signature, BODY, NOW));
    }

    @Test
    @DisplayName("timestamp가 숫자가 아니면 거부한다")
    void invalidTimestamp() {
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, "abc", BODY);

        assertRejected(() -> verify(verifier, WEBHOOK_ID, "abc", signature, BODY, NOW));
    }

    @Test
    @DisplayName("헤더가 하나라도 없거나 비어 있으면 거부한다")
    void missingHeaders() {
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

        assertRejected(() -> verify(verifier, null, TIMESTAMP, signature, BODY, NOW));
        assertRejected(() -> verify(verifier, WEBHOOK_ID, null, signature, BODY, NOW));
        assertRejected(() -> verify(verifier, WEBHOOK_ID, TIMESTAMP, null, BODY, NOW));
        assertRejected(() -> verify(verifier, " ", TIMESTAMP, signature, BODY, NOW));
        assertRejected(() -> verifier.verify(WEBHOOK_ID, TIMESTAMP, signature, null, NOW));
    }

    @ParameterizedTest(name = "시크릿 \"{0}\"")
    @ValueSource(strings = {"", " ", "whsec_", "whsec_***not-base64***"})
    @DisplayName("시크릿이 없거나 형식이 잘못되면 모든 요청을 거부한다")
    void secretNotConfigured(String secret) {
        PortOneWebhookVerifier notConfigured = verifierWith(secret);
        String signature = PortOneWebhookTestSigner.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

        assertRejected(() -> verify(notConfigured, WEBHOOK_ID, TIMESTAMP, signature, BODY, NOW));
    }

    private static PortOneWebhookVerifier verifierWith(String secret) {
        return new PortOneWebhookVerifier(new PortOneProperties(
                "https://api.portone.io", "test-api-secret", "test-store-id", "test-channel-key", secret));
    }

    private static void verify(PortOneWebhookVerifier target, String id, String timestamp, String signature,
            String body, Instant now) {
        target.verify(id, timestamp, signature, body.getBytes(StandardCharsets.UTF_8), now);
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PortOneWebhookErrorCode.INVALID_SIGNATURE);
    }
}
