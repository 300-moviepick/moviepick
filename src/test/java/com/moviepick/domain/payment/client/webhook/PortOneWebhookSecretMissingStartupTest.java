package com.moviepick.domain.payment.client.webhook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 운영에서 PORTONE_WEBHOOK_SECRET을 아직 넣지 않았어도 앱은 기동되고, 웹훅만 거부되는지 확인한다.
// 시크릿 형식별 거부 동작은 PortOneWebhookVerifierTest가 확인한다.
@SpringBootTest(properties = {
        "JWT_SECRET=bW92aWVwaWNrLXRlc3Qtb25seS1qd3Qtc2VjcmV0LWtleS1mb3ItY2khIQ==",
        "TMDB_ACCESS_TOKEN=test-dummy-tmdb-token",
        "portone.webhook-secret="
})
class PortOneWebhookSecretMissingStartupTest {

    private static final String OTHER_SECRET = "whsec_bW92aWVwaWNrLXRlc3Qtb25seS13ZWJob29rLXNlY3JldA==";
    private static final String WEBHOOK_ID = "msg_secret_missing";

    @Autowired
    PortOneWebhookVerifier verifier;

    @Test
    @DisplayName("웹훅 시크릿이 비어 있어도 앱은 기동되고, 웹훅 요청은 서명 오류로 거부한다")
    void startsWithoutSecretAndRejectsWebhook() {
        Instant now = Instant.now();
        String timestamp = String.valueOf(now.getEpochSecond());
        String body = PortOneWebhookTestSigner.paidBody("secret-missing");
        String signature = PortOneWebhookTestSigner.sign(OTHER_SECRET, WEBHOOK_ID, timestamp, body);

        assertThatThrownBy(() -> verifier.verify(
                WEBHOOK_ID, timestamp, signature, body.getBytes(StandardCharsets.UTF_8), now))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PortOneWebhookErrorCode.INVALID_SIGNATURE);
    }
}
