package com.linepay.reward.coupon;

/**
 * POST /coupon-issues, GET /coupon-issues/{requestId} 성공 응답
 */
public record CouponIssueResponse(String requestId, String couponId, String status) {

    public static final String ISSUED = "ISSUED";
}
