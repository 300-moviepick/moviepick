package com.moviepick.global.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private static final String ERROR_MESSAGE =
            "jwt.secret은 Base64 문자열이어야 하며 디코딩 후 32바이트 이상이어야 합니다.";

    @Test
    void 빈_문자열이면_예외가_발생한다() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtProvider("")
        );

        assertEquals(ERROR_MESSAGE, exception.getMessage());
    }

    @Test
    void 잘못된_Base64이면_예외가_발생한다() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtProvider("invalid!base64")
        );

        assertEquals(ERROR_MESSAGE, exception.getMessage());
    }

    @Test
    void 디코딩된_키가_32바이트_미만이면_예외가_발생한다() {
        String shortSecret = Base64.getEncoder()
                .encodeToString(new byte[16]);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtProvider(shortSecret)
        );

        assertEquals(ERROR_MESSAGE, exception.getMessage());
    }

    @Test
    void 디코딩된_키가_32바이트이면_정상_생성된다() {
        String validSecret = Base64.getEncoder()
                .encodeToString(new byte[32]);

        assertDoesNotThrow(() -> new JwtProvider(validSecret));
    }
}
