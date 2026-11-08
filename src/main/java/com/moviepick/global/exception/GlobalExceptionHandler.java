package com.moviepick.global.exception;

import com.moviepick.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 상태 코드만 있는 예외(ResponseStatusException 등)에 붙일 공통 오류 이름
    private static final Map<Integer, CommonErrorCode> CODE_BY_STATUS = Map.of(
            400, CommonErrorCode.INVALID_INPUT,
            401, CommonErrorCode.UNAUTHORIZED,
            403, CommonErrorCode.FORBIDDEN,
            404, CommonErrorCode.NOT_FOUND,
            405, CommonErrorCode.METHOD_NOT_ALLOWED,
            415, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
            500, CommonErrorCode.INTERNAL_ERROR
    );

    // 비즈니스 로직에서 발생하는 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("BusinessException: {}", e.getErrorCode());

        return respond(e.getErrorCode());
    }

    // @Valid DTO 필드 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException e) {

        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(CommonErrorCode.INVALID_INPUT.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT, message));
    }

    // 잘못된 요청 파라미터, JSON 형식, 제약 조건 위반, 빠진 헤더·쿠키 처리
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            ServletRequestBindingException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.warn("Bad request: {}", e.getMessage());

        return respond(CommonErrorCode.INVALID_INPUT);
    }

    // 없는 주소
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoPath(Exception e) {
        log.warn("No path: {}", e.getMessage());

        return respond(CommonErrorCode.PATH_NOT_FOUND);
    }

    // 지원하지 않는 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());

        return respond(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    // 지원하지 않는 요청 본문 형식 (예: text/plain)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type: {}", e.getMessage());

        return respond(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    // 응답 형식을 맞출 수 없는 Accept 헤더
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException e) {
        log.warn("Not acceptable: {}", e.getMessage());

        return respondWithStatus(HttpStatus.NOT_ACCEPTABLE, null);
    }

    // 상태 코드를 직접 지정한 예외 (ResponseStatusException 등)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException e) {
        log.warn("ResponseStatusException: {}", e.getMessage());

        return respondWithStatus(e.getStatusCode(), e.getReason());
    }

    // 데드락·락 획득 실패 등 동시 요청 충돌 → 409 (동시성 수정의 마지막 안전장치)
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrency(ConcurrencyFailureException e) {
        log.warn("Concurrent request conflict: {}", e.getClass().getSimpleName());

        return respond(CommonErrorCode.CONCURRENT_REQUEST);
    }

    // 예상하지 못한 서버 내부 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {

        // 상태 코드를 가진 Spring 예외는 그 상태 그대로 응답 (내부 메시지는 노출하지 않음)
        if (e instanceof ErrorResponse errorResponse
                && !errorResponse.getStatusCode().is5xxServerError()) {
            log.warn("Spring web exception: {}", e.getMessage());
            return respondWithStatus(errorResponse.getStatusCode(), null);
        }

        log.error("Unexpected error", e);

        return respond(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    // 상태 코드만 있을 때: 같은 상태의 공통 오류 이름을 붙이고, 없으면 HTTP 상태 이름 사용
    private ResponseEntity<ApiResponse<Void>> respondWithStatus(
            HttpStatusCode status, String reason) {

        CommonErrorCode common = CODE_BY_STATUS.get(status.value());
        HttpStatus resolved = HttpStatus.resolve(status.value());

        String name = common != null
                ? common.name()
                : (resolved != null ? resolved.name() : "HTTP_" + status.value());

        String message = reason != null && !reason.isBlank()
                ? reason
                : (common != null
                        ? common.getMessage()
                        : (resolved != null ? resolved.getReasonPhrase() : "요청을 처리할 수 없습니다."));

        return ResponseEntity.status(status)
                .body(ApiResponse.error(status, name, message));
    }
}
