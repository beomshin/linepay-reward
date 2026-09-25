package com.linepay.reward.common.response;

import com.linepay.reward.common.exception.ErrorCode;

/**
 * 공통 응답 포맷.
 * <pre>
 * 성공: { "code": "0000", "msg": "SUCCESS", "data": { ... } }
 * 실패: { "code": "MISSION_NOT_FOUND", "msg": "미션을 찾을 수 없습니다.", "data": null }
 * </pre>
 * 성공과 실패 응답 모두 {@code code}, {@code msg} 를 포함하며, 실패 응답의 {@code data} 는 항상 {@code null} 이다.
 * 실패 시 {@code code} 는 영문 사유 코드, {@code msg} 는 한글 메시지이다.
 */
public record ApiResponse<T>(String code, String msg, T data) {

    public static final String SUCCESS_CODE = "0000";
    public static final String SUCCESS_MSG = "SUCCESS";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MSG, data);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 실패 메시지에 상세 정보(예: 검증 실패 필드)를 덧붙인다. */
    public static ApiResponse<Void> fail(ErrorCode errorCode, String detail) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage() + " (" + detail + ")", null);
    }
}
