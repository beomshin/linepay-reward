package com.linepay.reward.coupon;

/**
 * 외부 쿠폰 시스템의 실패 결과 구분
 * 실제 HTTP 통신을 대신하므로 각 결과에 대응하는 HTTP 상태를 함께 표기한다.
 */
public enum CouponErrorType {
    /** GET /coupon-templates/{id} : 존재하지 않는 쿠폰 템플릿 (404) */
    TEMPLATE_NOT_FOUND(404),
    /** GET /coupon-issues/{requestId} : 확인되지 않는 requestId (404) */
    ISSUE_NOT_FOUND(404),
    /** POST /coupon-issues : 발급 한도 소진 (409) */
    QUANTITY_EXHAUSTED(409),
    /** POST /coupon-issues : 유효하지 않은 쿠폰 템플릿 - 미존재 또는 발급 중지 (400) */
    INVALID_TEMPLATE(400),
    /** POST /coupon-issues : 동일 requestId 에 서로 다른 내용 전달 (409) */
    REQUEST_ID_CONFLICT(409);

    private final int httpStatus;

    CouponErrorType(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
