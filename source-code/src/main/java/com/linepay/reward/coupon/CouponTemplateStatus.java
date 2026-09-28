package com.linepay.reward.coupon;

/** 외부 쿠폰 템플릿 상태  */
public enum CouponTemplateStatus {
    /** 발급 가능 */
    AVAILABLE,
    /** 발급 한도 소진 */
    EXHAUSTED,
    /** 발급 중지 */
    INACTIVE
}
