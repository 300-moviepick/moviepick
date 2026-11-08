package com.moviepick.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moviepick.global.exception.ErrorCode;
import org.springframework.http.HttpStatusCode;

/**
 * 모든 API의 공통 응답 모양: {success, code, message, data}.
 * 오류 응답에만 오류 이름(errorCode, 예: CSRF_INVALID)이 함께 붙는다.
 */
public record ApiResponse<T>(
        boolean success,
        int code,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode
) {

    // 기존 4개 값 생성자 호환
    public ApiResponse(boolean success, int code, String message, T data) {
        this(success, code, message, data, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, 200, "요청에 성공하였습니다.", data);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, 201, "생성되었습니다.", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getStatus().value(), message, null, nameOf(errorCode));
    }

    // 오류 응답에도 결과 데이터를 함께 보내야 하는 경우 (예: 실패한 배치 실행 정보)
    public static <T> ApiResponse<T> errorWithData(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.getStatus().value(), errorCode.getMessage(), data, nameOf(errorCode));
    }

    // enum 오류 코드가 없는 경우용
    public static <T> ApiResponse<T> error(HttpStatusCode status, String errorCode, String message) {
        return new ApiResponse<>(false, status.value(), message, null, errorCode);
    }

    // 오류 이름은 enum 상수 이름을 그대로 쓴다
    private static String nameOf(ErrorCode errorCode) {
        return errorCode instanceof Enum<?> e ? e.name() : null;
    }
}
