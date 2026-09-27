package com.linepay.reward.integration;

import com.linepay.reward.mission.domain.MissionParticipation;
import com.linepay.reward.mission.service.MissionService;
import com.linepay.reward.reward.domain.Reward;
import com.linepay.reward.reward.domain.RewardStatus;
import com.linepay.reward.reward.dto.RewardResponse;
import com.linepay.reward.reward.service.RewardService;
import com.linepay.reward.coupon.CouponTemplateStatus;
import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비즈니스 키(이력번호·리워드번호) 채번과 유니크 제약조건 검증 (교정 3)
 */
@DisplayName("[QA-K] 비즈니스 키 채번 / 유니크 제약")
class BusinessKeyTest extends IntegrationTestSupport {

    @Autowired
    MissionService missionService;
    @Autowired
    RewardService rewardService;

    /** 모든 작업을 동시에 출발시키고 결과를 모은다 (실패 시 예외 전파) */
    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("QA-K01 채번기 동시 호출 300건: 이력번호·리워드번호 모두 중복 없음, 형식 = 접두어 + 일자 + 10자리")
    void generatorUniqueUnderConcurrency() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 12, 0);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            tasks.add(() -> numberGenerator.nextParticipationNo(now));
            tasks.add(() -> numberGenerator.nextRewardNo(now));
        }

        List<String> numbers = runConcurrently(tasks);

        assertThat(new HashSet<>(numbers)).hasSize(300); // 중복제거 검증
        assertThat(numbers).allMatch(n -> n.matches("(PT|RW)20260901\\d{10}")); // 형식 검증
    }

    @Test
    @DisplayName("QA-K02 사용자 100명 동시 미션 완료: 이력번호 100개 모두 다름 (API 응답·DB 모두)")
    void participationNoUniqueUnderConcurrentComplete() throws Exception {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String userId = TEST_PREFIX + "USER_" + i;
            createUser(userId);
            tasks.add(() -> missionService.completeMission(userId, "MISSION_0002").participationNo());
        }

        List<String> numbers = runConcurrently(tasks);

        assertThat(new HashSet<>(numbers)).hasSize(100); // 중복제거 검증
        Set<String> stored = new HashSet<>();
        participationRepository.findAll().forEach(p -> stored.add(p.getParticipationNo()));
        assertThat(stored).hasSize(100).containsExactlyInAnyOrderElementsOf(numbers);
    }

    @Test
    @DisplayName("QA-K03 참여 이력 50건 보상 동시 요청: 리워드번호 50개 모두 다름")
    void rewardNoUniqueUnderConcurrentReward() throws Exception {
        List<Callable<RewardResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String userId = TEST_PREFIX + "USER_" + i;
            createUser(userId);
            String participationNo = missionService.completeMission(userId, "MISSION_0003").participationNo();
            tasks.add(() -> rewardService.requestReward(userId, participationNo));
        }

        List<RewardResponse> results = runConcurrently(tasks);

        assertThat(results).extracting(RewardResponse::rewardNo).doesNotHaveDuplicates().hasSize(50);
        assertThat(rewardRepository.count()).isEqualTo(50);
    }

    @Test
    @DisplayName("QA-K04 유니크 제약: 같은 이력번호로 참여 이력을 두 번 저장하면 DB 에서 거절")
    void participationNoUniqueConstraint() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 1, 12, 0);
        participationRepository.saveAndFlush(new MissionParticipation("PT202609019999999999", "MISSION_0002", "USER_0001", at));

        assertThatThrownBy(() -> participationRepository.saveAndFlush(
                new MissionParticipation("PT202609019999999999", "MISSION_0003", "USER_0002", at)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("QA-K05 유니크 제약: 같은 리워드번호 / 같은 참여 이력에 보상 결과 두 번 저장하면 DB 에서 거절")
    void rewardUniqueConstraints() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 1, 12, 0);
        MissionParticipation p1 = insertParticipation("MISSION_0002", "USER_0001", at);
        MissionParticipation p2 = insertParticipation("MISSION_0002", "USER_0002", at);
        Reward first = Reward.of("RW202609019999999999", p1);
        first.markNoReward(at);
        rewardRepository.saveAndFlush(first);

        // 1) 같은 리워드번호
        Reward sameRewardNo = Reward.of("RW202609019999999999", p2);
        sameRewardNo.markNoReward(at);
        assertThatThrownBy(() -> rewardRepository.saveAndFlush(sameRewardNo))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 2) 같은 참여 이력(이력번호)에 두 번째 보상 결과
        Reward sameParticipation = Reward.of("RW202609018888888888", p1);
        sameParticipation.markNoReward(at);
        assertThatThrownBy(() -> rewardRepository.saveAndFlush(sameParticipation))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("QA-K06 NO_REWARD 후 재요청하면 새로 채번하지 않고 같은 리워드번호로 지급된다")
    void retryKeepsRewardNo() {
        String missionId = TEST_PREFIX + "COUPON_MISSION";
        createMission(missionId);
        createCouponItem(TEST_PREFIX + "ITEM_C", missionId, "COUPON_TEMPLATE_0001");
        couponSystem.changeStatus("COUPON_TEMPLATE_0001", CouponTemplateStatus.INACTIVE);
        String participationNo = missionService.completeMission("USER_0001", missionId).participationNo();

        RewardResponse noReward = rewardService.requestReward("USER_0001", participationNo);
        couponSystem.changeStatus("COUPON_TEMPLATE_0001", CouponTemplateStatus.AVAILABLE);
        RewardResponse granted = rewardService.requestReward("USER_0001", participationNo);

        assertThat(noReward.rewardStatus()).isEqualTo(RewardStatus.NO_REWARD);
        assertThat(granted.rewardStatus()).isEqualTo(RewardStatus.GRANTED);
        assertThat(granted.rewardNo()).isEqualTo(noReward.rewardNo());
    }
}
