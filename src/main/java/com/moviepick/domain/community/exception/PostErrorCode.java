package com.moviepick.domain.community.exception;

import com.moviepick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostErrorCode implements ErrorCode {

    POST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "게시글을 찾을 수 없습니다."
    ),
    POST_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "게시글에 대한 권한이 없습니다."
    ),
    POST_INVALID_INPUT(
            HttpStatus.BAD_REQUEST,
            "게시글 입력값이 올바르지 않습니다."
    ),
    POST_ALREADY_LIKED(
            HttpStatus.CONFLICT,
            "이미 좋아요를 누른 게시글입니다."
    ),
    POST_LIKE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "게시글 좋아요를 찾을 수 없습니다."
    ),
    CANNOT_LIKE_OWN_POST(
            HttpStatus.BAD_REQUEST,
            "본인의 게시글에는 좋아요할 수 없습니다."
    );

    private final HttpStatus status;
    private final String message;
}
