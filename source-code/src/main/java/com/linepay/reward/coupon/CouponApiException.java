package com.linepay.reward.coupon;

/**
 * 외부 쿠폰 시스템 실패 응답을 표현하는 예외.
 */
public class CouponApiException extends RuntimeException {

    private final CouponErrorType errorType;

    public CouponApiException(CouponErrorType errorType) {
        super(errorType.name());
        this.errorType = errorType;
    }

    public CouponErrorType getErrorType() {
        return errorType;
    }

    public int getHttpStatus() {
        return errorType.getHttpStatus();
    }
}
