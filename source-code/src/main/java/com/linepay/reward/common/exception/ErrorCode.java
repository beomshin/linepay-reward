package com.linepay.reward.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서비스 에러 코드 정의.
 * <ul>
 *     <li>{@code code} : 실패 사유를 나타내는 영문 코드 (= enum 상수명, 예: MISSION_NOT_FOUND)</li>
 *     <li>{@code msg}  : 클라이언트가 이해하기 쉬운 한글 메시지</li>
 *     <li>{@code httpStatus} : 결과에 맞는 HTTP 상태 코드</li>
 * </ul>
 * 전역 예외 핸들러({@link GlobalExceptionHandler})에서 이 enum 을 기준으로 응답을 만든다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ---------- 400 잘못된 요청 (요청값 검증) ----------
    /** 필수 요청값 누락 (@NotBlank, @NotNull) */
    MISSING_REQUIRED_VALUE(HttpStatus.BAD_REQUEST, "필수 요청값이 누락되었습니다."),
    /** 요청값 형식 오류 (@Pattern, @Size, 타입 불일치) */
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "요청값 형식이 올바르지 않습니다."),
    /** 요청값 범위 오류 (@Positive 등) */
    OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "요청값이 허용 범위를 벗어났습니다."),

    // ---------- 404 대상 없음 ----------
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다."),
    /** 참여 이력이 없거나, 요청한 사용자의 참여 이력이 아님 */
    PARTICIPATION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션 참여 이력을 찾을 수 없습니다."),
    /** 해당 참여 이력에 대한 보상 지급 요청 결과가 없음 */
    REWARD_NOT_FOUND(HttpStatus.NOT_FOUND, "보상 지급 요청 이력이 없습니다."),
    /** 정의되지 않은 API 경로 */
    API_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 API를 찾을 수 없습니다."),

    // ---------- 405 ----------
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    // ---------- 409 중복·상태 충돌 ----------
    /** 미션 참여 가능 기간이 아님 (entry_start_at <= 현재 < entry_end_at 불만족) */
    MISSION_NOT_IN_PERIOD(HttpStatus.CONFLICT, "미션 참여 가능 기간이 아닙니다."),
    /** 미션별 전체 참여 횟수(100회) 초과 */
    MISSION_TOTAL_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "미션 전체 참여 횟수를 초과했습니다."),
    /** 사용자별 같은 미션 하루 참여 횟수(10회) 초과 */
    MISSION_DAILY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "오늘 이 미션에 참여할 수 있는 횟수를 모두 사용했습니다."),
    /** 직전 참여 후 1시간이 지나지 않음 */
    MISSION_REENTRY_COOLDOWN(HttpStatus.CONFLICT, "직전 참여 후 1시간이 지나야 다시 참여할 수 있습니다."),
    /** 이미 보상이 지급된 참여 이력 */
    REWARD_ALREADY_GRANTED(HttpStatus.CONFLICT, "이미 보상이 지급된 미션 참여입니다."),

    // ---------- 500/503 서버 오류 ----------
    /** 외부 쿠폰 시스템이 예상하지 못한 결과를 반환하거나 처리 중 예외 발생 - 보상 결과는 FAILED 로 저장됨 */
    COUPON_SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "쿠폰 발급 처리 중 오류가 발생했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    /** 쿠폰 시스템과 통신하지 못함 (IO 오류·타임아웃 등) - 보상 결과는 FAILED 로 저장됨 */
    COUPON_COMMUNICATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "쿠폰 시스템과 통신하지 못해 보상을 지급하지 못했습니다. 잠시 후 다시 요청해 주세요."),
    /** DB 연결 실패, 락 대기 초과 등 데이터 처리 오류 */
    DATABASE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "데이터 처리 중 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    /** 응답 code 값 (영문 사유 코드) */
    public String getCode() {
        return name();
    }
}
