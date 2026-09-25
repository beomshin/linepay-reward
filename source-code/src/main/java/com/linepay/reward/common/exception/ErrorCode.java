package com.linepay.reward.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스 에러 코드 정의.
 * <ul>
 *     <li>{@code code} : "E" + HTTP 상태 코드 (예: E404)</li>
 *     <li>{@code msg}  : enum 상수명 (예: MISSION_NOT_FOUND)</li>
 * </ul>
 * 전역 예외 핸들러({@link GlobalExceptionHandler})에서 이 enum 을 기준으로 응답을 만든다.
 */
public enum ErrorCode {

    // ---------- 400 잘못된 요청 ----------
    /** 경로 변수 형식 오류 등 요청 자체가 올바르지 않음 */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),

    // ---------- 404 대상 없음 ----------
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 참여 이력이 없거나, 요청한 사용자의 참여 이력이 아님 */
    PARTICIPATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 해당 참여 이력에 대한 보상 지급 요청 결과가 없음 */
    REWARD_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 정의되지 않은 API 경로 */
    API_NOT_FOUND(HttpStatus.NOT_FOUND),

    // ---------- 405 ----------
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    // ---------- 409 중복·상태 충돌 ----------
    /** 미션 참여 가능 기간이 아님 (entry_start_at <= 현재 < entry_end_at 불만족) */
    MISSION_NOT_IN_PERIOD(HttpStatus.CONFLICT),
    /** 미션별 전체 참여 횟수(100회) 초과 */
    MISSION_TOTAL_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    /** 사용자별 같은 미션 하루 참여 횟수(10회) 초과 */
    MISSION_DAILY_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    /** 직전 참여 후 1시간이 지나지 않음 */
    MISSION_REENTRY_COOLDOWN(HttpStatus.CONFLICT),
    /** 이미 보상이 지급된 참여 이력 */
    REWARD_ALREADY_GRANTED(HttpStatus.CONFLICT),

    // ---------- 500 서버 오류 ----------
    /** 외부 쿠폰 시스템이 예상하지 못한 결과를 반환함 */
    COUPON_SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /** 응답 code 값 (예: E404) */
    public String getCode() {
        return "E" + httpStatus.value();
    }
}
