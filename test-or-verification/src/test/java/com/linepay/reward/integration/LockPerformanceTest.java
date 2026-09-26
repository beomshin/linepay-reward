package com.linepay.reward.integration;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.coupon.CouponTemplateStatus;
import com.linepay.reward.mission.domain.MissionParticipation;
import com.linepay.reward.mission.service.MissionService;
import com.linepay.reward.reward.domain.Reward;
import com.linepay.reward.reward.domain.RewardStatus;
import com.linepay.reward.reward.service.RewardService;
import com.linepay.reward.support.IntegrationTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 제어 락 성능 검증 (교정 5).
 * <p>
 * 다수 스레드가 같은 미션에 동시에 참여 완료·보상 요청을 보내고 다음을 측정해 로그([PERF])로 보고한다.
 * <ul>
 *     <li>처리 시간(전체 경과, 요청당 평균·최대 응답 시간), 처리량(TPS)</li>
 *     <li>처리 건수(성공 / 정책상 거절)</li>
 *     <li>락 대기로 인한 실패 건수(락 대기 시간 초과), 기타 실패 건수</li>
 * </ul>
 * 시간 수치는 실행 환경에 따라 달라지므로 검증(assert)하지 않고, 정합성과 실패 건수만 검증한다.
 */
@Slf4j
@DisplayName("[QA-S] 동시성 제어 락 성능 (교정 5)")
class LockPerformanceTest extends IntegrationTestSupport {

    /** 동시 요청 수 단계 */
    private static final int[] LOADS = {10, 50, 100, 200};
    private static final String TABLE_HEADER =
            "| 구분 | 요청 수 | 전체 처리 시간 | TPS | 성공 | 정책상 거절 | 락 대기 실패 | 기타 실패 | 평균 응답 | 최대 응답 |";

    @Autowired MissionService missionService;
    @Autowired RewardService rewardService;

    private record Outcome(Object value, long latencyNanos) {
    }

    private record Report(String label, int requests, long elapsedMs, long success, Map<String, Long> rejected,
                          long lockFailures, List<String> otherFailures, double avgMs, double maxMs) {

        String line() {
            double tps = elapsedMs == 0 ? requests : requests * 1000.0 / elapsedMs;
            return String.format("| %s | %d | %d ms | %.0f | %d | %s | %d | %d | %.1f ms | %.1f ms |",
                    label, requests, elapsedMs, tps, success, rejected.isEmpty() ? "0" : rejected,
                    lockFailures, otherFailures.size(), avgMs, maxMs);
        }
    }

    /** 모든 작업을 준비시킨 뒤 동시에 출발시키고, 요청별 결과·응답 시간과 전체 경과 시간을 측정한다 */
    private Report run(String label, List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    long begin = System.nanoTime();
                    Object value;
                    try {
                        value = task.call();
                    } catch (Exception e) {
                        value = e;
                    }
                    return new Outcome(value, System.nanoTime() - begin);
                }));
            }
            ready.await();
            long begin = System.nanoTime();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(120, TimeUnit.SECONDS));
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
            return summarize(label, outcomes, elapsedMs);
        } finally {
            executor.shutdownNow();
        }
    }

    private Report summarize(String label, List<Outcome> outcomes, long elapsedMs) {
        long success = 0;
        long lockFailures = 0;
        Map<String, Long> rejected = new TreeMap<>();
        List<String> otherFailures = new ArrayList<>();
        for (Outcome outcome : outcomes) {
            Object value = outcome.value();
            if (value instanceof BusinessException e) {
                rejected.merge(e.getErrorCode().name(), 1L, Long::sum);
            } else if (value instanceof PessimisticLockingFailureException) {
                // 락 대기 시간(LOCK_TIMEOUT) 초과 등 락 획득 실패 (CannotAcquireLockException 포함)
                lockFailures++;
            } else if (value instanceof Exception e) {
                otherFailures.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            } else {
                success++;
            }
        }
        double avgMs = outcomes.stream().mapToLong(Outcome::latencyNanos).average().orElse(0) / 1_000_000.0;
        double maxMs = outcomes.stream().mapToLong(Outcome::latencyNanos).max().orElse(0) / 1_000_000.0;
        Report report = new Report(label, outcomes.size(), elapsedMs, success, rejected, lockFailures,
                otherFailures, avgMs, maxMs);
        if (!otherFailures.isEmpty()) {
            log.warn("[PERF] {} 기타 실패 {}", label, otherFailures);
        }
        return report;
    }

    private void printReport(String title, List<Report> reports) {
        StringBuilder sb = new StringBuilder("\n[PERF] ").append(title).append('\n').append(TABLE_HEADER).append('\n');
        reports.forEach(r -> sb.append(r.line()).append('\n'));
        log.info(sb.toString());
    }

    /** 같은 미션에 서로 다른 사용자 n명이 동시에 완료 요청하는 작업 목록 */
    private List<Callable<Object>> completeTasks(String missionId, String userPrefix, int n) {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String userId = userPrefix + i;
            createUser(userId);
            tasks.add(() -> missionService.completeMission(userId, missionId));
        }
        return tasks;
    }

    private List<MissionParticipation> participationsOf(String missionId) {
        return participationRepository.findAll().stream()
                .filter(p -> p.getMissionId().equals(missionId))
                .toList();
    }

    @Test
    @DisplayName("QA-S01 같은 미션에 10/50/100/200명 동시 완료 요청: 처리 시간·건수·락 대기 실패 측정, 중복 참여 없음")
    void concurrentCompleteByLoad() throws Exception {
        List<Report> reports = new ArrayList<>();
        for (int load : LOADS) {
            String missionId = TEST_PREFIX + "PERF_M" + load;
            createMission(missionId);

            Report report = run("완료 " + load + "건", completeTasks(missionId, TEST_PREFIX + "P" + load + "_", load));
            reports.add(report);

            int expectedSuccess = Math.min(load, 100);
            List<MissionParticipation> participations = participationsOf(missionId);
            // 처리 건수: 100건까지 성공, 초과분은 전체 참여 한도로 거절
            assertThat(report.success()).isEqualTo(expectedSuccess);
            assertThat(report.rejected().getOrDefault(ErrorCode.MISSION_TOTAL_LIMIT_EXCEEDED.name(), 0L))
                    .isEqualTo(load - expectedSuccess);
            // 락 대기로 인한 실패·기타 실패 없음
            assertThat(report.lockFailures()).isZero();
            assertThat(report.otherFailures()).isEmpty();
            // 정합성: 참여 이력은 성공 건수와 같고, 사용자별 중복 참여 없음
            assertThat(participations).hasSize(expectedSuccess);
            assertThat(participations.stream().map(MissionParticipation::getUserId).distinct().count())
                    .isEqualTo(expectedSuccess);
        }
        printReport("같은 미션 동시 완료 요청 (요청 수별)", reports);
    }

    @Test
    @DisplayName("QA-S02 참여 100건 후 이력마다 보상 요청 2번씩(200건) 동시 전송: 중복 보상 없음")
    void concurrentRewardNoDuplicate() throws Exception {
        String missionId = TEST_PREFIX + "PERF_REWARD";
        createMission(missionId);
        couponSystem.registerTemplate("TEST_TEMPLATE_PERF", "성능 테스트 쿠폰", 1000, 0, CouponTemplateStatus.AVAILABLE);
        createCouponItem(TEST_PREFIX + "PERF_ITEM", missionId, "TEST_TEMPLATE_PERF");

        // 1) 100명 동시 완료
        Report complete = run("완료 100건", completeTasks(missionId, TEST_PREFIX + "R_", 100));
        List<MissionParticipation> participations = participationsOf(missionId);
        assertThat(participations).hasSize(100);

        // 2) 참여 이력마다 같은 보상 요청 2번씩 동시 전송 (200건)
        List<Callable<Object>> rewardTasks = new ArrayList<>();
        for (MissionParticipation p : participations) {
            for (int i = 0; i < 2; i++) {
                rewardTasks.add(() -> rewardService.requestReward(p.getUserId(), p.getParticipationNo()));
            }
        }
        Report reward = run("보상 200건", rewardTasks);
        printReport("참여 완료 → 보상 요청 동시 처리", List.of(complete, reward));

        // 처리 건수: 이력당 1건만 처리, 나머지 1건은 이미 처리됨으로 거절
        assertThat(reward.success()).isEqualTo(100);
        assertThat(reward.rejected()).containsExactly(Map.entry(ErrorCode.REWARD_ALREADY_GRANTED.name(), 100L));
        assertThat(reward.lockFailures()).isZero();
        assertThat(reward.otherFailures()).isEmpty();

        // 정합성: 이력당 보상 1건, 발급된 쿠폰 수 = 쿠폰 지급 보상 수
        List<Reward> rewards = rewardRepository.findAll();
        assertThat(rewards).hasSize(100);
        assertThat(rewards.stream().map(Reward::getParticipationNo).distinct().count()).isEqualTo(100);
        long couponGranted = rewards.stream()
                .filter(r -> r.getRewardStatus() == RewardStatus.GRANTED && r.getCouponId() != null).count();
        assertThat(couponSystem.getCouponTemplate("TEST_TEMPLATE_PERF").issuedQuantity()).isEqualTo((int) couponGranted);
    }

    @Test
    @DisplayName("QA-S03 락 경합 비교: 같은 미션 100건 vs 서로 다른 미션 10개 x 10건")
    void lockContentionComparison() throws Exception {
        // 같은 미션: 100건이 미션 락 하나를 두고 순서대로 처리된다
        String sameMission = TEST_PREFIX + "PERF_SAME";
        createMission(sameMission);
        Report same = run("같은 미션 1개", completeTasks(sameMission, TEST_PREFIX + "S_", 100));

        // 서로 다른 미션: 미션마다 락이 달라 경합이 10건 단위로 나뉜다
        List<Callable<Object>> spread = new ArrayList<>();
        for (int m = 0; m < 10; m++) {
            String missionId = TEST_PREFIX + "PERF_SPREAD_" + m;
            createMission(missionId);
            spread.addAll(completeTasks(missionId, TEST_PREFIX + "D" + m + "_", 10));
        }
        Report different = run("서로 다른 미션 10개", spread);

        printReport("락 경합 비교 (각 100건)", List.of(same, different));
        Map<String, Long> perMission = participationRepository.findAll().stream()
                .collect(Collectors.groupingBy(MissionParticipation::getMissionId, Collectors.counting()));
        assertThat(same.success()).isEqualTo(100);
        assertThat(different.success()).isEqualTo(100);
        assertThat(same.lockFailures() + different.lockFailures()).isZero();
        assertThat(perMission.get(sameMission)).isEqualTo(100);
        for (int m = 0; m < 10; m++) {
            assertThat(perMission.get(TEST_PREFIX + "PERF_SPREAD_" + m)).isEqualTo(10);
        }
    }
}
