package com.moviepick.domain.payment.client.webhook;

/**
 * 서명 검증을 통과한 PortOne 웹훅에서 꺼낸 값.
 * 본문의 결제 상태·금액은 담지 않는다. 처리는 paymentId로 PortOne에 다시 조회한 결과로만 한다.
 *
 * @param webhookId 웹훅 메시지 ID (webhook-id 헤더, 재시도해도 같다)
 * @param type      이벤트 유형 (예: Transaction.Paid)
 * @param paymentId 결제 ID. 결제 이벤트가 아니면 null일 수 있다
 */
public record PortOneWebhookEvent(String webhookId, String type, String paymentId) {

    static final String TRANSACTION_PREFIX = "Transaction.";
    private static final String TRANSACTION_PAID = "Transaction.Paid";

    public boolean isTransactionPaid() {
        return TRANSACTION_PAID.equals(type);
    }
}
