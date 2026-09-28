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
 *
 * <h3>이 테스트가 하는 일</h3>
 * 여러 스레드가 "같은 미션"에 동시에 완료·보상 요청을 보내고, 미션 락 때문에 느려지거나 실패하는지 확인한다.
 * <ol>
 *     <li>요청 N건을 스레드 N개에 하나씩 맡기고, 출발 신호에 맞춰 한꺼번에 실행한다. ({@link #runAtOnce})</li>
 *     <li>요청마다 결과(성공 / 정책상 거절 / 락 대기 실패 / 기타 실패)와 걸린 시간을 모은다.</li>
 *     <li>결과를 표로 로그([PERF])에 출력하고, 건수와 정합성(중복 참여·중복 보상 없음)을 검증한다.</li>
 * </ol>
 * 시간 수치는 PC 성능에 따라 달라지므로 검증(assert)하지 않고 로그로만 남긴다.
 */
@Slf4j
@DisplayName("[QA-S] 동시성 제어 락 성능 (교정 5)")
class LockPerformanceTest extends IntegrationTestSupport {

    /** 동시 요청 수 단계: 요청이 늘어날수록 처리 시간·실패가 어떻게 변하는지 본다 */
    private static final int[] LOADS = {10, 50, 100, 200};

    @Autowired MissionService missionService;
    @Autowired RewardService rewardService;

    /** 요청 1건의 결과: 반환값(또는 발생한 예외)과 걸린 시간 */
    private record Response(Object value, long elapsedNanos) {
    }

    /**
     * 동시 요청 한 묶음의 집계 결과 (로그 표의 한 줄).
     * <ul>
     *     <li>success      : 정상 처리 건수</li>
     *     <li>rejected     : 정책상 거절 건수 (에러 코드별. 예: 전체 참여 한도 초과, 이미 지급됨)</li>
     *     <li>lockFailures : 락을 기다리다 시간 초과로 실패한 건수 → 0 이어야 락 병목이 실패로 이어지지 않은 것</li>
     *     <li>otherFailures: 그 밖의 예외 건수 → 0 이어야 함</li>
     * </ul>
     */
    private static class Result {
        String label;
        int requests;
        long totalMs;              // 첫 요청 출발 ~ 마지막 요청 완료까지 걸린 시간
        int success;
        Map<String, Long> rejected = new TreeMap<>();
        int lockFailures;
        int otherFailures;
        double avgMs;              // 요청 1건의 평균 응답 시간
        double maxMs;              // 가장 오래 걸린 요청의 응답 시간

        static final String HEADER =
                "| 구분 | 요청 수 | 전체 처리 시간 | TPS | 성공 | 정책상 거절 | 락 대기 실패 | 기타 실패 | 평균 응답 | 최대 응답 |";

        String toRow() {
            double tps = totalMs == 0 ? requests : requests * 1000.0 / totalMs;   // 초당 처리 건수
            return String.format("| %s | %d | %d ms | %.0f | %d | %s | %d | %d | %.1f ms | %.1f ms |",
                    label, requests, totalMs, tps, success, rejected.isEmpty() ? "0" : rejected,
                    lockFailures, otherFailures, avgMs, maxMs);
        }
    }

    /**
     * 요청들을 "동시에" 실행하고 결과를 집계한다.
     * <p>
     * 스레드를 만들자마자 실행하면 먼저 만든 스레드가 먼저 끝나 버려 동시 요청이 되지 않는다.
     * 그래서 모든 스레드가 준비될 때까지 기다렸다가(ready), 출발 신호(go)를 한 번에 준다.
     */
    private Result runAtOnce(String label, List<Callable<Object>> requests) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(requests.size());   // 요청 1건당 스레드 1개
        CountDownLatch ready = new CountDownLatch(requests.size());                // 모든 스레드 준비 완료 확인용
        CountDownLatch go = new CountDownLatch(1);                                 // 출발 신호
        try {
            // 1) 각 스레드에 요청을 맡긴다. 스레드는 준비 완료를 알리고 출발 신호를 기다린다.
            List<Future<Response>> futures = new ArrayList<>();
            for (Callable<Object> request : requests) {
                futures.add(threads.submit(() -> {
                    ready.countDown();
                    go.await();
                    long begin = System.nanoTime();
                    Object value;
                    try {
                        value = request.call();      // 성공하면 응답 객체
                    } catch (Exception e) {
                        value = e;                   // 실패하면 예외를 결과로 보관 (나중에 종류별로 센다)
                    }
                    return new Response(value, System.nanoTime() - begin);
                }));
            }

            // 2) 모든 스레드가 준비되면 출발 신호를 주고, 전체 시간 측정을 시작한다.
            ready.await();
            long begin = System.nanoTime();
            go.countDown();

            // 3) 모든 요청이 끝날 때까지 기다리며 결과를 모은다.
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get(120, TimeUnit.SECONDS));
            }
            long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);

            // 4) 결과를 종류별로 센다.
            return count(label, responses, totalMs);
        } finally {
            threads.shutdownNow();
        }
    }

    /** 요청 결과를 성공 / 정책상 거절 / 락 대기 실패 / 기타 실패로 나눠 센다 */
    private Result count(String label, List<Response> responses, long totalMs) {
        Result result = new Result();
        result.label = label;
        result.requests = responses.size();
        result.totalMs = totalMs;

        long sumNanos = 0;
        long maxNanos = 0;
        for (Response response : responses) {
            Object value = response.value();
            if (value instanceof BusinessException e) {
                // 정책상 거절 (예: MISSION_TOTAL_LIMIT_EXCEEDED, REWARD_ALREADY_GRANTED)
                result.rejected.merge(e.getErrorCode().name(), 1L, Long::sum);
            } else if (value instanceof PessimisticLockingFailureException) {
                // 락 대기 시간(H2 LOCK_TIMEOUT=10초) 초과 → 락 병목으로 인한 실패
                result.lockFailures++;
            } else if (value instanceof Exception e) {
                // 예상하지 못한 실패
                result.otherFailures++;
                log.warn("[PERF] {} 기타 실패 {}: {}", label, e.getClass().getSimpleName(), e.getMessage());
            } else {
                result.success++;
            }
            sumNanos += response.elapsedNanos();
            maxNanos = Math.max(maxNanos, response.elapsedNanos());
        }
        result.avgMs = responses.isEmpty() ? 0 : sumNanos / (double) responses.size() / 1_000_000.0;
        result.maxMs = maxNanos / 1_000_000.0;
        return result;
    }

    /** 결과를 표로 로그에 출력한다 (TEST_RESULT.md 에 옮겨 적는 값) */
    private void printReport(String title, List<Result> results) {
        StringBuilder table = new StringBuilder("\n[PERF] ").append(title).append('\n')
                .append(Result.HEADER).append('\n');
        results.forEach(r -> table.append(r.toRow()).append('\n'));
        log.info(table.toString());
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
        List<Result> results = new ArrayList<>();
        // 요청 수(10 → 50 → 100 → 200)를 늘려 가며 같은 실험을 반복한다. 단계마다 새 미션을 쓴다.
        for (int load : LOADS) {
            String missionId = TEST_PREFIX + "PERF_M" + load;
            createMission(missionId);

            // 서로 다른 사용자 load명이 같은 미션에 동시에 완료 요청
            Result result = runAtOnce("완료 " + load + "건", completeTasks(missionId, TEST_PREFIX + "P" + load + "_", load));
            results.add(result);

            int expectedSuccess = Math.min(load, 100);
            List<MissionParticipation> participations = participationsOf(missionId);
            // 처리 건수: 미션 전체 참여 한도가 100회이므로 100건까지 성공, 초과분은 한도 초과로 거절
            assertThat(result.success).isEqualTo(expectedSuccess);
            assertThat(result.rejected.getOrDefault(ErrorCode.MISSION_TOTAL_LIMIT_EXCEEDED.name(), 0L))
                    .isEqualTo(load - expectedSuccess);
            // 락 대기로 인한 실패·기타 실패 없음
            assertThat(result.lockFailures).isZero();
            assertThat(result.otherFailures).isZero();
            // 정합성: 참여 이력은 성공 건수와 같고, 사용자별 중복 참여 없음
            assertThat(participations).hasSize(expectedSuccess);
            assertThat(participations.stream().map(MissionParticipation::getUserId).distinct().count())
                    .isEqualTo(expectedSuccess);
        }
        printReport("같은 미션 동시 완료 요청 (요청 수별)", results);
    }

    @Test
    @DisplayName("QA-S02 참여 100건 후 이력마다 보상 요청 2번씩(200건) 동시 전송: 중복 보상 없음")
    void concurrentRewardNoDuplicate() throws Exception {
        String missionId = TEST_PREFIX + "PERF_REWARD";
        createMission(missionId);
        couponSystem.registerTemplate("TEST_TEMPLATE_PERF", "성능 테스트 쿠폰", 1000, 0, CouponTemplateStatus.AVAILABLE);
        createCouponItem(TEST_PREFIX + "PERF_ITEM", missionId, "TEST_TEMPLATE_PERF");

        // 1) 100명 동시 완료
        Result complete = runAtOnce("완료 100건", completeTasks(missionId, TEST_PREFIX + "R_", 100));
        List<MissionParticipation> participations = participationsOf(missionId);
        assertThat(participations).hasSize(100);

        // 2) 참여 이력마다 같은 보상 요청 2번씩 동시 전송 (200건)
        List<Callable<Object>> rewardTasks = new ArrayList<>();
        for (MissionParticipation p : participations) {
            for (int i = 0; i < 2; i++) {
                rewardTasks.add(() -> rewardService.requestReward(p.getUserId(), p.getParticipationNo()));
            }
        }
        Result reward = runAtOnce("보상 200건", rewardTasks);
        printReport("참여 완료 → 보상 요청 동시 처리", List.of(complete, reward));

        // 처리 건수: 이력당 1건만 처리, 나머지 1건은 이미 처리됨으로 거절
        assertThat(reward.success).isEqualTo(100);
        assertThat(reward.rejected).containsExactly(Map.entry(ErrorCode.REWARD_ALREADY_GRANTED.name(), 100L));
        assertThat(reward.lockFailures).isZero();
        assertThat(reward.otherFailures).isZero();

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
        Result same = runAtOnce("같은 미션 1개", completeTasks(sameMission, TEST_PREFIX + "S_", 100));

        // 서로 다른 미션: 미션마다 락이 달라 경합이 10건 단위로 나뉜다
        List<Callable<Object>> spread = new ArrayList<>();
        for (int m = 0; m < 10; m++) {
            String missionId = TEST_PREFIX + "PERF_SPREAD_" + m;
            createMission(missionId);
            spread.addAll(completeTasks(missionId, TEST_PREFIX + "D" + m + "_", 10));
        }
        Result different = runAtOnce("서로 다른 미션 10개", spread);

        // 두 결과의 처리 시간을 비교하면 락 병목의 크기를 알 수 있다.
        // (같은 미션이 훨씬 느리면 → 미션 락 때문에 한 건씩 처리되는 병목이 있다는 뜻)
        printReport("락 경합 비교 (각 100건)", List.of(same, different));

        // 정합성: 두 경우 모두 100건 성공, 락 대기 실패 없음, 미션별 참여 건수 정확
        Map<String, Long> perMission = participationRepository.findAll().stream()
                .collect(Collectors.groupingBy(MissionParticipation::getMissionId, Collectors.counting()));
        assertThat(same.success).isEqualTo(100);
        assertThat(different.success).isEqualTo(100);
        assertThat(same.lockFailures + different.lockFailures).isZero();
        assertThat(perMission.get(sameMission)).isEqualTo(100);
        for (int m = 0; m < 10; m++) {
            assertThat(perMission.get(TEST_PREFIX + "PERF_SPREAD_" + m)).isEqualTo(10);
        }
    }
}
