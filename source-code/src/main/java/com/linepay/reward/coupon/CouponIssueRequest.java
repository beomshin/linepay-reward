package com.linepay.reward.coupon;

/**
 * POST /coupon-issues 요청 (과제 8.2)
 *
 * @param requestId        멱등 키. 동일 requestId + 동일 내용이면 기존 발급 결과를 반환한다.
 * @param userId           발급 대상 사용자
 * @param couponTemplateId 쿠폰 템플릿 ID
 */
public record CouponIssueRequest(String requestId, String userId, String couponTemplateId) {
}
