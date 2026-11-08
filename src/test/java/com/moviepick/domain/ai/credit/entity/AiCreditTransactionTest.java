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

        AiCreditTransaction transaction = use(wallet, requestKey);

        assertEquals(AiCreditEventType.USE, transaction.getEventType());
        assertEquals(-1, transaction.getDelta());
        assertEquals(requestKey, transaction.getRequestKey());
    }

    @Test
    void 추천_성공시_잔액_변화는_0이고_사용과_같은_요청을_가리킨다() {
        AiCreditWallet wallet = createWallet();
        String requestKey = UUID.randomUUID().toString();
        AiCreditTransaction originalUse = use(wallet, requestKey);

        AiCreditTransaction transaction =
                AiCreditTransaction.complete(originalUse, "COMPLETE:MEMBER:10:REQ:" + requestKey);

        assertEquals(AiCreditEventType.COMPLETE, transaction.getEventType());
        assertEquals(0, transaction.getDelta());
        assertEquals(requestKey, transaction.getRequestKey());
        assertEquals(AiCreditBucket.FREE, transaction.getBucket());
        assertSame(wallet, transaction.getWallet());
    }

    @Test
    void 추천_실패시_플러스_1을_기록한다() {
        AiCreditWallet wallet = createWallet();
        String requestKey = UUID.randomUUID().toString();
        AiCreditTransaction originalUse = use(wallet, requestKey);

        AiCreditTransaction transaction =
                AiCreditTransaction.restore(originalUse, "RESTORE:MEMBER:10:REQ:" + requestKey);

        assertEquals(AiCreditEventType.RESTORE, transaction.getEventType());
        assertEquals(1, transaction.getDelta());
        assertEquals(AiCreditBucket.FREE, transaction.getBucket());
    }

    @Test
    void USE가_아닌_거래는_완료나_복구할_수_없다() {
        AiCreditTransaction grant = AiCreditTransaction.freeGrant(createWallet(), 3, "FREE_GRANT:MEMBER:10");

        assertThrows(IllegalArgumentException.class, () -> AiCreditTransaction.complete(grant, "COMPLETE:X"));
        assertThrows(IllegalArgumentException.class, () -> AiCreditTransaction.restore(grant, "RESTORE:X"));
    }

    @Test
    void 요청_키가_UUID_길이가_아니거나_멱등_키가_비면_사용_기록을_거부한다() {
        AiCreditWallet wallet = createWallet();

        assertThrows(IllegalArgumentException.class,
                () -> AiCreditTransaction.use(wallet, AiCreditBucket.FREE, "short", "USE:MEMBER:10:REQ:short"));
        assertThrows(IllegalArgumentException.class,
                () -> AiCreditTransaction.use(wallet, AiCreditBucket.FREE, UUID.randomUUID().toString(), " "));
    }

    @Test
    void 잘못된_지급_수량은_거부한다() {
        AiCreditWallet wallet = createWallet();

        assertThrows(
                IllegalArgumentException.class,
                () -> AiCreditTransaction.freeGrant(wallet, 0, "TEST:GRANT")
        );
    }

    private AiCreditTransaction use(AiCreditWallet wallet, String requestKey) {
        return AiCreditTransaction.use(wallet, AiCreditBucket.FREE, requestKey, "USE:MEMBER:10:REQ:" + requestKey);
    }
}
