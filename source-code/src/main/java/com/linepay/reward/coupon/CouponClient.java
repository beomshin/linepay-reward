package com.linepay.reward.coupon;

/**
 * 외부 쿠폰 시스템 클라이언트 계약 (과제 8절).
 * <p>
 * 리워드 서비스는 이 인터페이스에만 의존한다.
 * 실제 HTTP 통신 대신 {@link FakeCouponSystem} 이 계약에 정의된 동작을 재현한다.
 * 실 연동 시 HTTP 구현체로 교체하면 된다.
 */
public interface CouponClient {

    /**
     * GET /coupon-templates/{couponTemplateId}
     *
     * @throws CouponApiException {@link CouponErrorType#TEMPLATE_NOT_FOUND}
     */
    CouponTemplateResponse getCouponTemplate(String couponTemplateId);

    /**
     * POST /coupon-issues
     *
     * @throws CouponApiException {@link CouponErrorType#QUANTITY_EXHAUSTED},
     *                            {@link CouponErrorType#INVALID_TEMPLATE},
     *                            {@link CouponErrorType#REQUEST_ID_CONFLICT}
     */
    CouponIssueResponse issueCoupon(CouponIssueRequest request);

    /**
     * GET /coupon-issues/{requestId}
     *
     * @throws CouponApiException {@link CouponErrorType#ISSUE_NOT_FOUND}
     */
    CouponIssueResponse getCouponIssue(String requestId);
}
