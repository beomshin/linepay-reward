package com.linepay.reward.common.exception;

import com.linepay.reward.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 핸들러.
 * <p>
 * 모든 예외를 {@link ErrorCode} 로 변환해 공통 응답 포맷({@code data = null})으로 반환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 비즈니스 규칙 위반 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        if (e.getErrorCode().getHttpStatus().is5xxServerError()) {
            log.error("[EXC] 서버 오류: {} ({})", e.getErrorCode(), e.getErrorCode().getMessage(), e);
        } else {
            log.info("[EXC] 비즈니스 거절: {} ({})", e.getErrorCode(), e.getErrorCode().getMessage());
        }
        return toResponse(e.getErrorCode());
    }

    /**
     * 컨트롤러 요청값 검증 실패 (Spring MVC 내장 메서드 검증, @PathVariable 등).
     * 첫 번째 위반 항목의 제약 이름(메시지 코드 목록의 마지막 값, 예: "NotBlank")으로 에러 코드를 정한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        ParameterValidationResult result = e.getParameterValidationResults().get(0);
        String[] codes = result.getResolvableErrors().get(0).getCodes();
        String constraint = (codes == null || codes.length == 0) ? "" : codes[codes.length - 1];
        return toValidationResponse(classify(constraint), result.getMethodParameter().getParameterName());
    }

    /** 서비스 계층 등에서 발생한 Bean Validation 위반 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        ConstraintViolation<?> violation = e.getConstraintViolations().iterator().next();
        String constraint = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        String path = violation.getPropertyPath().toString();
        String field = path.substring(path.lastIndexOf('.') + 1);
        return toValidationResponse(classify(constraint), field);
    }

    /** 경로 변수 타입 불일치 (숫자형 경로 변수에 숫자가 아닌 값) → 형식 오류 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return toValidationResponse(ErrorCode.INVALID_FORMAT, e.getName());
    }

    /** 경로 변수 누락 → 필수값 누락 */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPathVariable(MissingPathVariableException e) {
        return toValidationResponse(ErrorCode.MISSING_REQUIRED_VALUE, e.getVariableName());
    }

    /** 정의되지 않은 경로 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        log.info("[EXC] 정의되지 않은 API 경로: {}", e.getResourcePath());
        return toResponse(ErrorCode.API_NOT_FOUND);
    }

    /** 지원하지 않는 HTTP Method */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.info("[EXC] 지원하지 않는 HTTP 메서드: {}", e.getMethod());
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 그 외 예상하지 못한 오류 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("[EXC] 예상하지 못한 오류", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 제약 이름 → 검증 에러 코드.
     * 필수값(NotBlank, NotNull) → 필수값 누락, 범위(Positive) → 범위 오류, 그 외 → 형식 오류
     */
    private ErrorCode classify(String constraint) {
        return switch (constraint) {
            case "NotBlank", "NotNull" -> ErrorCode.MISSING_REQUIRED_VALUE;
            case "Positive" -> ErrorCode.OUT_OF_RANGE;
            default -> ErrorCode.INVALID_FORMAT;
        };
    }

    private ResponseEntity<ApiResponse<Void>> toValidationResponse(ErrorCode errorCode, String field) {
        log.info("[EXC] 요청값 검증 실패: {} 필드={}", errorCode, field);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode, field));
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
    }
}
