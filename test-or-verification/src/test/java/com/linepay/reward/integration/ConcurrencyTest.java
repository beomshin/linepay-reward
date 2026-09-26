package com.linepay.reward.integration;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.coupon.CouponTemplateStatus;
import com.linepay.reward.mission.service.MissionService;
import com.linepay.reward.reward.domain.RewardStatus;
import com.linepay.reward.reward.dto.RewardResponse;
import com.linepay.reward.reward.service.RewardService;
import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 과제 5절: "같은 요청이 반복되거나 여러 요청이 비슷한 시점에 전달될 수 있다"
 * 동시에 요청이 몰려도 참여/보상 정책이 깨지지 않는지 검증한다.
 */
@DisplayName("[QA-X] 반복·동시 요청")
class ConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    MissionService missionService;
    @Autowired
    RewardService rewardService;

    /** 모든 작업을 동시에 출발시키고 결과(성공 값 또는 에러 코드)를 모은다 */
    private <T> List<Object> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(tasks.size(), 32));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return (Object) task.call();
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<Object, AtomicInteger> countBy(List<Object> results) {
        Map<Object, AtomicInteger> counts = new ConcurrentHashMap<>();
        for (Object r : results) {
            Object key = (r instanceof ErrorCode) ? r : "SUCCESS";
            counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        }
        return counts;
    }

    @Test
    @DisplayName("QA-X01 같은 사용자가 같은 미션 완료를 20번 동시에 요청하면 1건만 생성된다")
    void sameUserConcurrentComplete() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> missionService.completeMission("USER_0001", "MISSION_0002"));
        }

        Map<Object, AtomicInteger> counts = countBy(runConcurrently(tasks));

        assertThat(counts.get("SUCCESS").get()).isEqualTo(1);
        assertThat(counts.get(ErrorCode.MISSION_REENTRY_COOLDOWN).get()).isEqualTo(19);
        assertThat(participationRepository.countByMissionId("MISSION_0002")).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-X02 서로 다른 사용자 130명이 동시에 참여해도 미션 전체 참여는 정확히 100건")
    void totalLimitUnderConcurrency() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 130; i++) {
            String userId = TEST_PREFIX + "USER_" + i;
            createUser(userId);
            tasks.add(() -> missionService.completeMission(userId, "MISSION_0003"));
        }

        Map<Object, AtomicInteger> counts = countBy(runConcurrently(tasks));

        assertThat(counts.get("SUCCESS").get()).isEqualTo(100);
        assertThat(counts.get(ErrorCode.MISSION_TOTAL_LIMIT_EXCEEDED).get()).isEqualTo(30);
        assertThat(participationRepository.countByMissionId("MISSION_0003")).isEqualTo(100);
    }

    @Test
    @DisplayName("QA-X03 같은 참여 이력에 보상 요청 10번 동시 전송 시 1번만 지급된다")
    void sameParticipationConcurrentReward() throws Exception {
        String participationNo = missionService.completeMission("USER_0001", "MISSION_0002").participationNo();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> rewardService.requestReward("USER_0001", participationNo));
        }

        Map<Object, AtomicInteger> counts = countBy(runConcurrently(tasks));

        assertThat(counts.get("SUCCESS").get()).isEqualTo(1);
        assertThat(counts.get(ErrorCode.REWARD_ALREADY_GRANTED).get()).isEqualTo(9);
        assertThat(rewardRepository.count()).isEqualTo(1);
        // 쿠폰이 선택됐더라도 최대 1개만 발급
        assertThat(couponSystem.getCouponTemplate("COUPON_TEMPLATE_0001").issuedQuantity()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("QA-X04 한도 5개 쿠폰에 10건의 보상 요청이 동시에 몰려도 쿠폰은 5개만 발급된다")
    void couponQuotaUnderConcurrency() throws Exception {
        couponSystem.registerTemplate("TEST_TEMPLATE_FIVE", "5개 한정", 5, 0, CouponTemplateStatus.AVAILABLE);
        String missionId = TEST_PREFIX + "COUPON_MISSION";
        createMission(missionId);
        createCouponItem(TEST_PREFIX + "ITEM_C", missionId, "TEST_TEMPLATE_FIVE");

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String userId = TEST_PREFIX + "USER_" + i;
            createUser(userId);
            String participationNo = missionService.completeMission(userId, missionId).participationNo();
            tasks.add(() -> rewardService.requestReward(userId, participationNo));
        }

        List<Object> results = runConcurrently(tasks);

        long granted = results.stream()
                .filter(r -> r instanceof RewardResponse rr && rr.rewardStatus() == RewardStatus.GRANTED).count();
        long noReward = results.stream()
                .filter(r -> r instanceof RewardResponse rr && rr.rewardStatus() == RewardStatus.NO_REWARD).count();
        assertThat(granted).isEqualTo(5);
        assertThat(noReward).isEqualTo(5);
        assertThat(couponSystem.getCouponTemplate("TEST_TEMPLATE_FIVE").issuedQuantity()).isEqualTo(5);
    }
}
