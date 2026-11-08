package com.moviepick.domain.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviepick.domain.payment.exception.PaymentErrorCode;
import com.moviepick.global.exception.BusinessException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class PortOneRestClient implements PortOneClient {

    private final RestClient restClient;
    private final PortOneProperties properties;

    public PortOneRestClient(@Qualifier("portOneApiRestClient") RestClient restClient,
            PortOneProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Optional<PortOnePayment> getPayment(String paymentId) {
        try {
            PaymentBody body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/payments/{paymentId}")
                            .queryParamIfPresent("storeId", Optional.ofNullable(properties.storeId())
                                    .filter(storeId -> !storeId.isBlank()))
                            .build(paymentId))
                    .retrieve()
                    .body(PaymentBody.class);

            if (body == null) {
                log.warn("PortOne 결제 조회 응답이 비어 있음: paymentId={}", paymentId);
                throw new BusinessException(PaymentErrorCode.PORTONE_REQUEST_FAILED);
            }
            Long total = body.amount() == null ? null : body.amount().total();
            return Optional.of(new PortOnePayment(PortOnePayment.Status.from(body.status()), total, body.currency()));

        } catch (HttpClientErrorException.NotFound e) {
            // 결제창을 아직 열지 않아 PortOne에 결제 건이 없는 경우 (PAYMENT_NOT_FOUND)
            return Optional.empty();
        } catch (RestClientResponseException e) {
            log.warn("PortOne 결제 조회 실패: paymentId={}, httpStatus={}", paymentId, e.getStatusCode().value());
            throw new BusinessException(PaymentErrorCode.PORTONE_REQUEST_FAILED);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                log.warn("PortOne 결제 조회 타임아웃: paymentId={}", paymentId);
                throw new BusinessException(PaymentErrorCode.PORTONE_TIMEOUT);
            }
            log.warn("PortOne 연결 실패: paymentId={}, cause={}", paymentId, e.getClass().getSimpleName());
            throw new BusinessException(PaymentErrorCode.PORTONE_REQUEST_FAILED);
        } catch (RestClientException e) {
            // 응답 본문 변환 실패 등
            log.warn("PortOne 응답 처리 실패: paymentId={}, cause={}", paymentId, e.getClass().getSimpleName());
            throw new BusinessException(PaymentErrorCode.PORTONE_REQUEST_FAILED);
        }
    }

    private boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** PortOne 응답 중 필요한 필드만 받는다. 나머지 필드는 무시한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentBody(String status, Amount amount, String currency) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Amount(Long total) {
        }
    }
}
