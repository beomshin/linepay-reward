package com.linepay.reward.integration;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.coupon.CouponIssueRequest;
import com.linepay.reward.coupon.CouponIssueResponse;
import com.linepay.reward.coupon.CouponTemplateStatus;
import com.linepay.reward.mission.domain.ItemType;
import com.linepay.reward.mission.service.MissionService;
import com.linepay.reward.reward.domain.RewardStatus;
import com.linepay.reward.reward.dto.RewardResponse;
import com.linepay.reward.reward.service.RewardService;
import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("[QA-R] 보상 지급 요청 / 결과 조회 (과제 7절)")
class RewardServiceTest extends IntegrationTestSupport {

    private static final String COUPON_TEMPLATE = "COUPON_TEMPLATE_0001";

    @Autowired
    MissionService missionService;
    @Autowired
    RewardService rewardService;

    private String complete(String userId, String missionId) {
        return missionService.completeMission(userId, missionId).participationNo();
    }

    private ErrorCode errorOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
        throw new AssertionError("BusinessException 이 발생해야 합니다.");
    }

    /** 쿠폰 아이템만 가진 테스트 미션 */
    private String couponOnlyMission(String templateId) {
        String missionId = TEST_PREFIX + "COUPON_MISSION";
        createMission(missionId);
        createCouponItem(TEST_PREFIX + "ITEM_C", missionId, templateId);
        return missionId;
    }

    @Test
    @DisplayName("QA-R01 포인트 전용 미션(0003) 보상은 5 이상 10 이하 포인트")
    void pointReward() {
        String participationNo = complete("USER_0001", "MISSION_0003");

        RewardResponse reward = rewardService.requestReward("USER_0001", participationNo);

        assertThat(reward.rewardStatus()).isEqualTo(RewardStatus.GRANTED);
        assertThat(reward.itemType()).isEqualTo(ItemType.REWARD_POINT);
        assertThat(reward.missionItemId()).isEqualTo("ITEM_0004");
        assertThat(reward.pointAmount()).isBetween(5, 10);
        assertThat(reward.couponId()).isNull();
    }

    @Test
    @DisplayName("QA-R02 포인트 값은 5~10 범위 안에서 무작위로 결정된다 (500회 샘플)")
    void pointRangeDistribution() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            int point = randomizer.nextPoint();
            assertThat(point).isBetween(5, 10);
            seen.add(point);
        }
        assertThat(seen).containsExactlyInAnyOrder(5, 6, 7, 8, 9, 10);
    }

    @Test
    @DisplayName("QA-R03 한 참여 이력에는 보상을 한 번만 지급 (재요청 시 REWARD_ALREADY_GRANTED, 결과 불변)")
    void rewardOnlyOnce() {
        String participationNo = complete("USER_0001", "MISSION_0003");
        RewardResponse first = rewardService.requestReward("USER_0001", participationNo);

        assertThat(errorOf(() -> rewardService.requestReward("USER_0001", participationNo)))
                .isEqualTo(ErrorCode.REWARD_ALREADY_GRANTED);
        assertThat(rewardService.getReward("USER_0001", participationNo)).isEqualTo(first);
        assertThat(rewardRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-R04 보상 요청 전 결과 조회는 REWARD_NOT_FOUND")
    void rewardNotRequestedYet() {
        String participationNo = complete("USER_0001", "MISSION_0003");

        assertThat(errorOf(() -> rewardService.getReward("USER_0001", participationNo)))
                .isEqualTo(ErrorCode.REWARD_NOT_FOUND);
    }

    @Test
    @DisplayName("QA-R05 다른 사용자의 참여 이력이나 없는 참여 이력은 PARTICIPATION_NOT_FOUND")
    void participationOwnership() {
        String participationNo = complete("USER_0001", "MISSION_0003");

        assertThat(errorOf(() -> rewardService.requestReward("USER_0002", participationNo)))
                .isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND);
        assertThat(errorOf(() -> rewardService.getReward("USER_0002", participationNo)))
                .isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND);
        assertThat(errorOf(() -> rewardService.requestReward("USER_0001", "PT_NOT_EXISTS")))
                .isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND);
        assertThat(errorOf(() -> rewardService.requestReward("NO_USER", participationNo)))
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("QA-R06 MISSION_0002 에서 쿠폰이 선택되면 외부 시스템으로 쿠폰 1개가 발급된다")
    void couponReward() {
        String participationNo = complete("USER_0001", "MISSION_0002");
        randomizer.forcePicks("ITEM_0003"); // [ITEM_0002(포인트), ITEM_0003(쿠폰)] 중 쿠폰 선택

        RewardResponse reward = rewardService.requestReward("USER_0001", participationNo);

        assertThat(reward.rewardStatus()).isEqualTo(RewardStatus.GRANTED);
        assertThat(reward.itemType()).isEqualTo(ItemType.COUPON);
        assertThat(reward.couponTemplateId()).isEqualTo(COUPON_TEMPLATE);
        assertThat(reward.couponId()).isNotBlank();
        assertThat(reward.pointAmount()).isNull();
        assertThat(couponSystem.getCouponTemplate(COUPON_TEMPLATE).issuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-R07 MISSION_0002 는 무작위 선택으로 포인트와 쿠폰이 모두 지급될 수 있다")
    void randomSelectionCoversBothTypes() {
        Set<ItemType> types = EnumSet.noneOf(ItemType.class);
        randomizer.forcePicks("ITEM_0002");
        types.add(rewardService.requestReward("USER_0001", complete("USER_0001", "MISSION_0002")).itemType());
        randomizer.forcePicks("ITEM_0003");
        types.add(rewardService.requestReward("USER_0002", complete("USER_0002", "MISSION_0002")).itemType());

        assertThat(types).containsExactlyInAnyOrder(ItemType.REWARD_POINT, ItemType.COUPON);
    }

    @Test
    @DisplayName("QA-R08 쿠폰이 발급 중지(INACTIVE)면 선택 대상에서 제외되어 포인트만 지급된다")
    void inactiveCouponExcluded() {
        couponSystem.changeStatus(COUPON_TEMPLATE, CouponTemplateStatus.INACTIVE);
        for (String user : new String[]{"USER_0001", "USER_0002", "USER_0003"}) {
            randomizer.forcePicks("ITEM_0003"); // 제외되지 않았다면 선택될 쿠폰 아이템
            RewardResponse reward = rewardService.requestReward(user, complete(user, "MISSION_0002"));
            assertThat(reward.itemType()).isEqualTo(ItemType.REWARD_POINT);
        }
        assertThat(couponSystem.getCouponTemplate(COUPON_TEMPLATE).issuedQuantity()).isZero();
    }

    @Test
    @DisplayName("QA-R09 쿠폰 한도 소진(EXHAUSTED)이면 선택 대상에서 제외된다")
    void exhaustedCouponExcluded() {
        couponSystem.registerTemplate(COUPON_TEMPLATE, "10% 할인 쿠폰", 100, 100, CouponTemplateStatus.EXHAUSTED);
        randomizer.forcePicks("ITEM_0003");

        RewardResponse reward = rewardService.requestReward("USER_0001", complete("USER_0001", "MISSION_0002"));

        assertThat(reward.itemType()).isEqualTo(ItemType.REWARD_POINT);
    }

    @Test
    @DisplayName("QA-R10 지급 가능한 보상이 없으면 NO_REWARD 반환, 이후 재요청 시 지급 가능")
    void noRewardThenRetry() {
        String missionId = couponOnlyMission(COUPON_TEMPLATE);
        couponSystem.changeStatus(COUPON_TEMPLATE, CouponTemplateStatus.INACTIVE);
        String participationNo = complete("USER_0001", missionId);

        RewardResponse noReward = rewardService.requestReward("USER_0001", participationNo);
        assertThat(noReward.rewardStatus()).isEqualTo(RewardStatus.NO_REWARD);
        assertThat(noReward.itemType()).isNull();
        assertThat(rewardService.getReward("USER_0001", participationNo).rewardStatus()).isEqualTo(RewardStatus.NO_REWARD);

        // 쿠폰 발급 재개 후 같은 참여 이력으로 재요청
        couponSystem.changeStatus(COUPON_TEMPLATE, CouponTemplateStatus.AVAILABLE);
        clock.advance(Duration.ofMinutes(5));
        RewardResponse granted = rewardService.requestReward("USER_0001", participationNo);

        assertThat(granted.rewardStatus()).isEqualTo(RewardStatus.GRANTED);
        assertThat(granted.itemType()).isEqualTo(ItemType.COUPON);
        assertThat(granted.processedTime()).isEqualTo("120500");
        assertThat(rewardRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-R11 외부 시스템에 존재하지 않는 쿠폰 템플릿만 있으면 NO_REWARD")
    void unknownTemplateMeansNoReward() {
        String missionId = couponOnlyMission("COUPON_TEMPLATE_UNKNOWN");

        RewardResponse reward = rewardService.requestReward("USER_0001", complete("USER_0001", missionId));

        assertThat(reward.rewardStatus()).isEqualTo(RewardStatus.NO_REWARD);
    }

    @Test
    @DisplayName("QA-R12 외부 발급은 성공했지만 저장 전에 실패했던 경우, 재요청 시 같은 쿠폰으로 복구된다")
    void recoverAlreadyIssuedCoupon() {
        String missionId = couponOnlyMission(COUPON_TEMPLATE);
        String participationNo = complete("USER_0001", missionId);
        // 이전 시도에서 외부 발급만 성공한 상황을 재현 (requestId 규칙: REWARD_{participationNo}_{templateId})
        CouponIssueResponse issuedBefore = couponSystem.issueCoupon(new CouponIssueRequest(
                "REWARD_" + participationNo + "_" + COUPON_TEMPLATE, "USER_0001", COUPON_TEMPLATE));

        RewardResponse reward = rewardService.requestReward("USER_0001", participationNo);

        assertThat(reward.couponId()).isEqualTo(issuedBefore.couponId());
        assertThat(couponSystem.getCouponTemplate(COUPON_TEMPLATE).issuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-R13 한도 1개 쿠폰: 첫 참여는 쿠폰, 다음 참여는 NO_REWARD (쿠폰 전용 미션)")
    void couponQuotaConsumed() {
        couponSystem.registerTemplate("TEST_TEMPLATE_ONE", "1개 한정", 1, 0, CouponTemplateStatus.AVAILABLE);
        String missionId = couponOnlyMission("TEST_TEMPLATE_ONE");

        RewardResponse first = rewardService.requestReward("USER_0001", complete("USER_0001", missionId));
        RewardResponse second = rewardService.requestReward("USER_0002", complete("USER_0002", missionId));

        assertThat(first.itemType()).isEqualTo(ItemType.COUPON);
        assertThat(second.rewardStatus()).isEqualTo(RewardStatus.NO_REWARD);
    }
}
