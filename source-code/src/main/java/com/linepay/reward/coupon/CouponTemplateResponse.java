package com.linepay.reward.coupon;

/**
 * GET /coupon-templates/{couponTemplateId} 성공 응답 (과제 8.1)
 */
public record CouponTemplateResponse(
        String couponTemplateId,
        String title,
        int maxQuantity,
        int issuedQuantity,
        CouponTemplateStatus status
) {
    /** 발급 가능 여부: AVAILABLE 상태이면서 한도가 남아 있어야 한다 */
    public boolean isIssuable() {
        return status == CouponTemplateStatus.AVAILABLE && issuedQuantity < maxQuantity;
    }
}
