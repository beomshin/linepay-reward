package com.linepay.reward.common.response;

import com.linepay.reward.common.exception.ErrorCode;

/**
 * 공통 응답 포맷.
 * <pre>
 * { "code": "0000", "msg": "SUCCESS", "data": { ... } }
 * </pre>
 * 성공과 실패 응답 모두 {@code code}, {@code msg} 를 포함하며, 실패 응답의 {@code data} 는 항상 {@code null} 이다.
 */
public record ApiResponse<T>(String code, String msg, T data) {

    public static final String SUCCESS_CODE = "0000";
    public static final String SUCCESS_MSG = "SUCCESS";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MSG, data);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.name(), null);
    }
}
