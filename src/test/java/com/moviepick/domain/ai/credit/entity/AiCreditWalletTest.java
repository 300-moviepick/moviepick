package com.moviepick.domain.ai.credit.entity;

import com.moviepick.domain.member.entity.Member;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiCreditWalletTest {

    private Member createMember() {
        return Member.createLocal(
                "test@example.com",
                "encoded-password",
                "테스트회원"
        );
    }

    @Test
    void 지갑_생성시_잔액은_0이다() {
        AiCreditWallet wallet = new AiCreditWallet(createMember());

        assertEquals(0, wallet.getFreeAvailable());
        assertEquals(0, wallet.getPaidAvailable());
    }

    @Test
    void 무료_크레딧을_지급할_수_있다() {
        AiCreditWallet wallet = new AiCreditWallet(createMember());

        wallet.grantFreeCredits(3);

        assertEquals(3, wallet.getFreeAvailable());
        assertEquals(0, wallet.getPaidAvailable());
    }

    @Test
    void 잘못된_지급_수량은_거부한다() {
        AiCreditWallet wallet = new AiCreditWallet(createMember());

        assertThrows(
                IllegalArgumentException.class,
                () -> wallet.grantFreeCredits(0)
        );

        assertEquals(0, wallet.getFreeAvailable());
    }
}