package com.linepay.reward.common.exception;

/**
 * 비즈니스 규칙 위반 시 던지는 예외.
 * 전역 예외 핸들러에서 {@link ErrorCode} 에 맞는 HTTP 상태와 응답으로 변환된다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
