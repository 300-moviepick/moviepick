package com.moviepick.domain.payment.client.webhook;

import com.moviepick.domain.payment.client.PortOneProperties;
import com.moviepick.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PortOne V2 웹훅 서명 검증 (Standard Webhooks 규격).
 *
 * <ul>
 *   <li>헤더: webhook-id, webhook-timestamp(초 단위 Unix 시각), webhook-signature(공백으로 구분한 "v1,{base64}" 목록)</li>
 *   <li>서명 대상: "{webhook-id}.{webhook-timestamp}.{요청 원문}" — 원문은 파싱 전 바이트 그대로 쓴다</li>
 *   <li>알고리즘: HMAC-SHA256, 키는 시크릿에서 "whsec_" 접두사를 떼고 base64 디코드한 값</li>
 * </ul>
 * 로그에는 webhook-id, webhook-timestamp만 남긴다. 서명 값, 시크릿, 요청 원문은 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneWebhookVerifier {

    /**
     * webhook-timestamp 허용 범위 (과거·미래 모두). 바꿀 때는 이 값 하나만 고친다.
     * 근거: PortOne 공식 서버 SDK의 허용 범위(5분). Standard Webhooks 규격상 재시도마다 timestamp가 새로 붙는다.
     */
    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final String SECRET_PREFIX = "whsec_";
    private static final String SIGNATURE_VERSION_PREFIX = "v1,";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_LOG_VALUE_LENGTH = 100;

    private final PortOneProperties properties;

    /**
     * @throws BusinessException INVALID_SIGNATURE (헤더 누락, timestamp 형식 오류·허용 범위 초과, 시크릿 미설정, 서명 불일치)
     */
    public void verify(String webhookId, String webhookTimestamp, String signatureHeader, byte[] rawBody) {
        verify(webhookId, webhookTimestamp, signatureHeader, rawBody, Instant.now());
    }

    void verify(String webhookId, String webhookTimestamp, String signatureHeader, byte[] rawBody, Instant now) {
        if (isBlank(webhookId) || isBlank(webhookTimestamp) || isBlank(signatureHeader) || rawBody == null) {
            throw reject("MISSING_HEADER", webhookId, webhookTimestamp);
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(webhookTimestamp.trim());
        } catch (NumberFormatException e) {
            throw reject("INVALID_TIMESTAMP", webhookId, webhookTimestamp);
        }
        if (Math.abs(now.getEpochSecond() - timestampSeconds) > TOLERANCE.toSeconds()) {
            throw reject("EXPIRED", webhookId, webhookTimestamp);
        }

        byte[] key = secretKey();
        if (key == null) {
            throw reject("SECRET_NOT_CONFIGURED", webhookId, webhookTimestamp);
        }

        byte[] expected = sign(key, webhookId, webhookTimestamp.trim(), rawBody);
        if (!matchesAny(expected, signatureHeader)) {
            throw reject("MISMATCH", webhookId, webhookTimestamp);
        }

        log.info("PortOne 웹훅 서명 검증 통과: webhookId={}, webhookTimestamp={}",
                safe(webhookId), safe(webhookTimestamp));
    }

    private byte[] secretKey() {
        String secret = properties.webhookSecret();
        if (isBlank(secret)) {
            log.error("PortOne 웹훅 시크릿(PORTONE_WEBHOOK_SECRET)이 설정되지 않아 웹훅을 처리할 수 없음");
            return null;
        }
        String encoded = secret.trim();
        if (encoded.startsWith(SECRET_PREFIX)) {
            encoded = encoded.substring(SECRET_PREFIX.length());
        }
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            if (key.length == 0) {
                log.error("PortOne 웹훅 시크릿(PORTONE_WEBHOOK_SECRET) 값이 비어 있음");
                return null;
            }
            return key;
        } catch (IllegalArgumentException e) {
            log.error("PortOne 웹훅 시크릿(PORTONE_WEBHOOK_SECRET)이 base64 형식이 아님");
            return null;
        }
    }

    private byte[] sign(byte[] key, String webhookId, String webhookTimestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            mac.update((webhookId + "." + webhookTimestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(rawBody);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없음", e);
        }
    }

    // 시크릿 교체 중에는 서명이 여러 개 올 수 있으므로 하나라도 맞으면 통과
    private boolean matchesAny(byte[] expected, String signatureHeader) {
        for (String candidate : signatureHeader.trim().split(" +")) {
            if (!candidate.startsWith(SIGNATURE_VERSION_PREFIX)) {
                continue;
            }
            try {
                byte[] actual = Base64.getDecoder().decode(candidate.substring(SIGNATURE_VERSION_PREFIX.length()));
                if (MessageDigest.isEqual(expected, actual)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // base64가 아닌 항목은 불일치로 본다
            }
        }
        return false;
    }

    private BusinessException reject(String reason, String webhookId, String webhookTimestamp) {
        log.warn("PortOne 웹훅 서명 검증 실패: reason={}, webhookId={}, webhookTimestamp={}",
                reason, safe(webhookId), safe(webhookTimestamp));
        return new BusinessException(PortOneWebhookErrorCode.INVALID_SIGNATURE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 외부에서 온 헤더·본문 값을 로그에 남길 때 줄바꿈·제어 문자를 지우고 길이를 자른다. */
    static String safe(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "_");
        return cleaned.length() > MAX_LOG_VALUE_LENGTH ? cleaned.substring(0, MAX_LOG_VALUE_LENGTH) + "..." : cleaned;
    }
}
