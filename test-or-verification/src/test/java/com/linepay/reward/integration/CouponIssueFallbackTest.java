package com.linepay.reward.integration;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.coupon.CouponApiException;
import com.linepay.reward.coupon.CouponErrorType;
import com.linepay.reward.coupon.FakeCouponSystem;
import com.linepay.reward.mission.domain.ItemType;
import com.linepay.reward.mission.service.MissionService;
import com.linepay.reward.reward.domain.RewardStatus;
import com.linepay.reward.reward.dto.RewardResponse;
import com.linepay.reward.reward.service.RewardService;
import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 템플릿 조회 시점엔 발급 가능했지만, 실제 발급 시점에 거절되는 상황(경쟁 상태)을 재현한다.
 */
@DisplayName("[QA-F] 쿠폰 발급 시점 실패 처리")
class CouponIssueFallbackTest extends IntegrationTestSupport {

    @MockitoSpyBean
    FakeCouponSystem couponSpy;
    @Autowired
    MissionService missionService;
    @Autowired
    RewardService rewardService;

    @Test
    @DisplayName("QA-F01 발급 시점에 한도 소진되면 쿠폰을 후보에서 제외하고 포인트로 다시 선정한다")
    void fallbackToPointWhenExhaustedAtIssue() {
        doThrow(new CouponApiException(CouponErrorType.QUANTITY_EXHAUSTED)).when(couponSpy).issueCoupon(any());
        Long participationId = missionService.completeMission("USER_0001", "MISSION_0002").participationId();
        randomizer.forceIndexes(1); // 먼저 쿠폰 선택

        RewardResponse reward = rewardService.requestReward("USER_0001", participationId);

        assertThat(reward.rewardStatus()).isEqualTo(RewardStatus.GRANTED);
        assertThat(reward.itemType()).isEqualTo(ItemType.REWARD_POINT);
    }

    @Test
    @DisplayName("QA-F02 예상하지 못한 외부 오류(REQUEST_ID_CONFLICT)는 COUPON_SYSTEM_ERROR, 보상 결과는 저장되지 않는다")
    void unexpectedCouponError() {
        doThrow(new CouponApiException(CouponErrorType.REQUEST_ID_CONFLICT)).when(couponSpy).issueCoupon(any());
        Long participationId = missionService.completeMission("USER_0001", "MISSION_0002").participationId();
        randomizer.forceIndexes(1);

        assertThatThrownBy(() -> rewardService.requestReward("USER_0001", participationId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COUPON_SYSTEM_ERROR);
        assertThat(rewardRepository.findByParticipationId(participationId)).isEmpty();
    }
}
