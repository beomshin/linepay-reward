package com.linepay.reward.common.exception;

import com.linepay.reward.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;

/**
 * 전역 예외 핸들러.
 * <p>
 * 모든 예외를 {@link ErrorCode} 로 변환해 공통 응답 포맷({@code data = null})으로 반환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 필수값 누락으로 분류할 Bean Validation 제약 */
    private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotBlank", "NotNull", "NotEmpty");
    /** 범위 오류로 분류할 Bean Validation 제약 */
    private static final Set<String> RANGE_CONSTRAINTS =
            Set.of("Positive", "PositiveOrZero", "Min", "Max", "DecimalMin", "DecimalMax");

    /** 비즈니스 규칙 위반 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        if (e.getErrorCode().getHttpStatus().is5xxServerError()) {
            log.error("business error: {}", e.getErrorCode(), e);
        } else {
            log.info("business rejected: {}", e.getErrorCode());
        }
        return toResponse(e.getErrorCode());
    }

    /**
     * 컨트롤러 요청값 검증 실패 (Spring MVC 내장 메서드 검증, @PathVariable 등).
     * 첫 번째 위반 제약의 종류로 필수값 누락 / 형식 오류 / 범위 오류를 구분한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        // 한 값이 여러 제약을 동시에 위반할 수 있으므로(예: 공백 → NotBlank + Pattern)
        // 필수값 누락 > 범위 오류 > 형식 오류 순으로 가장 우선하는 사유 하나를 응답한다.
        ErrorCode selected = null;
        String selectedField = null;
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                ErrorCode errorCode = classify(constraintName(result, error));
                if (selected == null || priority(errorCode) < priority(selected)) {
                    selected = errorCode;
                    selectedField = field;
                }
            }
        }
        if (selected == null) {
            return toResponse(ErrorCode.INVALID_FORMAT);
        }
        return toValidationResponse(selected, selectedField);
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

    /** 경로 변수 타입 불일치 (예: participationId 에 숫자가 아닌 값) → 형식 오류 */
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
        return toResponse(ErrorCode.API_NOT_FOUND);
    }

    /** 지원하지 않는 HTTP Method */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 그 외 예상하지 못한 오류 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /** 검증 에러 우선순위 (작을수록 우선) */
    private int priority(ErrorCode errorCode) {
        return switch (errorCode) {
            case MISSING_REQUIRED_VALUE -> 0;
            case OUT_OF_RANGE -> 1;
            default -> 2;
        };
    }

    /** 제약 어노테이션 이름 → 검증 에러 코드 */
    private ErrorCode classify(String constraint) {
        if (REQUIRED_CONSTRAINTS.contains(constraint)) {
            return ErrorCode.MISSING_REQUIRED_VALUE;
        }
        if (RANGE_CONSTRAINTS.contains(constraint)) {
            return ErrorCode.OUT_OF_RANGE;
        }
        return ErrorCode.INVALID_FORMAT;
    }

    /**
     * 위반한 제약 어노테이션 이름(예: NotBlank)을 꺼낸다.
     * ConstraintViolation 으로 꺼낼 수 없으면 메시지 코드 목록의 마지막 값(예: "NotBlank")을 사용한다.
     */
    private String constraintName(ParameterValidationResult result, MessageSourceResolvable error) {
        try {
            ConstraintViolation<?> violation = result.unwrap(error, ConstraintViolation.class);
            return violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        } catch (IllegalArgumentException ignored) {
            String[] codes = error.getCodes();
            if (codes == null || codes.length == 0) {
                return "";
            }
            String last = codes[codes.length - 1];
            int dot = last.indexOf('.');
            return dot < 0 ? last : last.substring(0, dot);
        }
    }

    private ResponseEntity<ApiResponse<Void>> toValidationResponse(ErrorCode errorCode, String field) {
        log.info("request validation failed: {} field={}", errorCode, field);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode, field));
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
    }
}
