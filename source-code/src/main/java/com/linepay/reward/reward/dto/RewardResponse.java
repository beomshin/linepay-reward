package com.linepay.reward.reward.dto;

import com.linepay.reward.mission.domain.ItemType;
import com.linepay.reward.reward.domain.Reward;
import com.linepay.reward.reward.domain.RewardStatus;

/**
 * 보상 지급 요청/결과 조회 응답.
 * <ul>
 *     <li>포인트 지급: rewardStatus=GRANTED, itemType=REWARD_POINT, pointAmount=5~10</li>
 *     <li>쿠폰 지급  : rewardStatus=GRANTED, itemType=COUPON, couponTemplateId/couponId</li>
 *     <li>보상 없음  : rewardStatus=NO_REWARD, 보상 관련 필드는 null</li>
 * </ul>
 */
public record RewardResponse(
        Long participationId,
        String missionId,
        String userId,
        RewardStatus rewardStatus,
        String missionItemId,
        ItemType itemType,
        Integer pointAmount,
        String couponTemplateId,
        String couponId,
        String processedDate,
        String processedTime
) {
    public static RewardResponse from(Reward reward) {
        return new RewardResponse(
                reward.getParticipationId(),
                reward.getMissionId(),
                reward.getUserId(),
                reward.getRewardStatus(),
                reward.getMissionItemId(),
                reward.getItemType(),
                reward.getPointAmount(),
                reward.getCouponTemplateId(),
                reward.getCouponId(),
                reward.getProcessedDate(),
                reward.getProcessedTime());
    }
}
