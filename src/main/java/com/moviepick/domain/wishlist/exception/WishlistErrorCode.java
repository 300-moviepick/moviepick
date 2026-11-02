package com.moviepick.domain.wishlist.exception;

import com.moviepick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WishlistErrorCode implements ErrorCode {

    ALREADY_WISHLISTED(
            HttpStatus.CONFLICT,
            "이미 찜한 영화입니다."
    ),
    WISHLIST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "찜한 영화가 아닙니다."
    );

    private final HttpStatus status;
    private final String message;
}
