package com.moviepick.domain.support.client.webhook;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 테스트에서 PortOne(Standard Webhooks) 형식의 서명을 만든다. 운영 코드와 독립적으로 계산한다. */
public final class PortOneWebhookTestSigner {

    private PortOneWebhookTestSigner() {
    }

    /** @return "v1,{base64(HMAC-SHA256(id.timestamp.body))}" */
    public static String sign(String secret, String webhookId, String timestamp, String body) {
        try {
            String encoded = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(encoded), "HmacSHA256"));
            byte[] digest = mac.doFinal((webhookId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String paidBody(String paymentId) {
        return """
                {"type":"Transaction.Paid","timestamp":"2026-11-06T10:00:00.000Z",\
                "data":{"storeId":"store-test","paymentId":"%s","transactionId":"tx-1"}}""".formatted(paymentId);
    }
}
