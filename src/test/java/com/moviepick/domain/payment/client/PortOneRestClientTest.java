package com.moviepick.domain.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moviepick.domain.payment.exception.PaymentErrorCode;
import com.moviepick.global.exception.BusinessException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** PortOne 요청 형식(주소·인증 헤더)과 응답·오류 변환을 가짜 서버로 확인한다. */
class PortOneRestClientTest {

    private static final String BASE_URL = "https://api.portone.io";
    private static final String PAYMENT_ID = "support-client-test";
    private static final String PAYMENT_URL = BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=test-store-id";

    private MockRestServiceServer server;
    private PortOneRestClient client;

    @BeforeEach
    void setUp() {
        PortOneProperties properties = new PortOneProperties(
                BASE_URL, "test-api-secret", "test-store-id", "test-channel-key", "test-webhook-secret");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + properties.apiSecret());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneRestClient(builder.build(), properties);
    }

    @Test
    @DisplayName("GET /payments/{paymentId}에 PortOne 인증 헤더를 붙여 조회하고 상태·금액·통화를 꺼낸다")
    void getPaidPayment() {
        server.expect(requestTo(PAYMENT_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne test-api-secret"))
                .andRespond(withSuccess("""
                        {
                          "status": "PAID",
                          "id": "support-client-test",
                          "orderName": "MoviePick 개발자 후원 - 커피 1잔",
                          "amount": { "total": 3000, "taxFree": 0, "vat": 273, "paid": 3000 },
                          "currency": "KRW",
                          "paidAt": "2026-10-21T03:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<PortOnePayment> payment = client.getPayment(PAYMENT_ID);

        assertThat(payment).contains(new PortOnePayment(PortOnePayment.Status.PAID, 3_000L, "KRW"));
        server.verify();
    }

    @Test
    @DisplayName("문서에 없는 상태 값은 UNKNOWN으로 변환한다")
    void unknownStatus() {
        server.expect(requestTo(PAYMENT_URL))
                .andRespond(withSuccess("""
                        { "status": "SOMETHING_NEW", "amount": { "total": 3000 }, "currency": "KRW" }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getPayment(PAYMENT_ID))
                .contains(new PortOnePayment(PortOnePayment.Status.UNKNOWN, 3_000L, "KRW"));
    }

    @Test
    @DisplayName("404 PAYMENT_NOT_FOUND면 empty를 반환한다")
    void paymentNotFound() {
        server.expect(requestTo(PAYMENT_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "type": "PAYMENT_NOT_FOUND", "message": "결제 건이 존재하지 않습니다." }
                                """));

        assertThat(client.getPayment(PAYMENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("401 UNAUTHORIZED 등 그 외 오류 응답은 PORTONE_REQUEST_FAILED")
    void unauthorized() {
        server.expect(requestTo(PAYMENT_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "type": "UNAUTHORIZED", "message": "인증 정보가 올바르지 않습니다." }
                                """));

        assertThatThrownBy(() -> client.getPayment(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PORTONE_REQUEST_FAILED);
    }

    @Test
    @DisplayName("타임아웃은 PORTONE_TIMEOUT")
    void timeout() {
        server.expect(requestTo(PAYMENT_URL))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> client.getPayment(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PORTONE_TIMEOUT);
    }
}
