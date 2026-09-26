package com.linepay.reward.reward.service;

import com.linepay.reward.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 쿠폰 API 호출 실패 (교정 4).
 * <p>
 * 쿠폰 API(getCouponTemplate, issueCoupon, getCouponIssue) 호출 중 처리할 수 없는 예외가 나면 던진다.
 * {@link RewardService#requestReward} 가 받아 보상 결과를 FAILED 로 저장한 뒤 정의된 에러 코드로 응답한다.
 * <ul>
 *     <li>IO 오류·타임아웃 → {@link ErrorCode#COUPON_COMMUNICATION_FAILED}</li>
 *     <li>예상하지 못한 쿠폰 시스템 응답·그 외 예외 → {@link ErrorCode#COUPON_SYSTEM_ERROR}</li>
 * </ul>
 */
@Getter
public class CouponFailureException extends RuntimeException {

    private final ErrorCode errorCode;

    public CouponFailureException(String operation, ErrorCode errorCode, Throwable cause) {
        super(operation + " 실패 (" + cause.getClass().getSimpleName() + ")", cause);
        this.errorCode = errorCode;
    }
}
