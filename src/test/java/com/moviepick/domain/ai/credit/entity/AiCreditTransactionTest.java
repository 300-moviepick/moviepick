
package com.moviepick.domain.ai.credit.entity;

import com.moviepick.domain.member.entity.Member;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiCreditTransactionTest {

    private AiCreditWallet createWallet() {
        Member member = Member.createLocal(
                "test@example.com",
                "encoded-password",
                "테스트회원"
        );
        return new AiCreditWallet(member);
    }

    @Test
    void 무료_크레딧_지급_기록을_생성한다() {
        AiCreditWallet wallet = createWallet();

        AiCreditTransaction transaction =
                AiCreditTransaction.freeGrant(
                        wallet, 3, "FREE_GRANT:MEMBER:10"
                );

        assertEquals(AiCreditEventType.FREE_GRANT, transaction.getEventType());
        assertEquals(AiCreditBucket.FREE, transaction.getBucket());
        assertEquals(3, transaction.getDelta());
        assertSame(wallet, transaction.getWallet());
    }

    @Test
    void 추천_사용시_마이너스_1을_기록한다() {
        AiCreditWallet wallet = createWallet();
        String requestKey = UUID.randomUUID().toString();

        AiCreditTransaction transaction =
                AiCreditTransaction.use(
                        wallet,
                        requestKey,
                        AiCreditBucket.FREE,
                        "AI:USE:10:" + requestKey
                );

        assertEquals(AiCreditEventType.USE, transaction.getEventType());
        assertEquals(-1, transaction.getDelta());
        assertEquals(requestKey, transaction.getRequestKey());
    }

    @Test
    void 추천_성공시_잔액_변화는_0이다() {
        AiCreditWallet wallet = createWallet();
        String requestKey = UUID.randomUUID().toString();

        AiCreditTransaction transaction =
                AiCreditTransaction.complete(
                        wallet,
                        requestKey,
                        AiCreditBucket.FREE,
                        "AI:FINAL:10:" + requestKey
                );

        assertEquals(AiCreditEventType.COMPLETE, transaction.getEventType());
        assertEquals(0, transaction.getDelta());
    }

    @Test
    void 추천_실패시_플러스_1을_기록한다() {
        AiCreditWallet wallet = createWallet();
        String requestKey = UUID.randomUUID().toString();

        AiCreditTransaction transaction =
                AiCreditTransaction.restore(
                        wallet,
                        requestKey,
                        AiCreditBucket.FREE,
                        "AI:FINAL:10:" + requestKey
                );

        assertEquals(AiCreditEventType.RESTORE, transaction.getEventType());
        assertEquals(1, transaction.getDelta());
    }

    @Test
    void 잘못된_지급_수량은_거부한다() {
        AiCreditWallet wallet = createWallet();

        assertThrows(
                IllegalArgumentException.class,
                () -> AiCreditTransaction.freeGrant(wallet, 0, "TEST:GRANT")
        );
    }
}
