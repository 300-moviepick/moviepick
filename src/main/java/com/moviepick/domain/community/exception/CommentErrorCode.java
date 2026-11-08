package com.moviepick.domain.community.exception;

import com.moviepick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {

    COMMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "댓글을 찾을 수 없습니다."
    ),
    COMMENT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "댓글에 대한 권한이 없습니다."
    ),
    COMMENT_ALREADY_LIKED(
            HttpStatus.CONFLICT,
            "이미 좋아요를 누른 댓글입니다."
    ),
    COMMENT_LIKE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "댓글 좋아요를 찾을 수 없습니다."
    ),
    CANNOT_LIKE_OWN_COMMENT(
            HttpStatus.BAD_REQUEST,
            "본인의 댓글에는 좋아요할 수 없습니다."
    ),
    // 신고 승인으로 숨긴 댓글의 좋아요·수정·삭제 (작성자 본인 포함)
    COMMENT_BLINDED(
            HttpStatus.FORBIDDEN,
            "관리자에 의해 가려진 댓글입니다."
    );

    private final HttpStatus status;
    private final String message;
}
