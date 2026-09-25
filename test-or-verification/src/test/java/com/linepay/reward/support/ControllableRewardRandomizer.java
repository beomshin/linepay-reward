package com.linepay.reward.support;

import com.linepay.reward.reward.service.RewardRandomizer;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 테스트용 무작위 생성기.
 * 값을 지정하지 않으면 실제 무작위 동작을 그대로 사용하고,
 * 지정하면 큐에 넣은 순서대로 값을 반환해 보상 선정 결과를 결정적으로 만든다.
 */
public class ControllableRewardRandomizer extends RewardRandomizer {

    private final ConcurrentLinkedQueue<Integer> forcedIndexes = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Integer> forcedPoints = new ConcurrentLinkedQueue<>();

    public void reset() {
        forcedIndexes.clear();
        forcedPoints.clear();
    }

    public void forceIndexes(Integer... indexes) {
        forcedIndexes.addAll(java.util.List.of(indexes));
    }

    public void forcePoints(Integer... points) {
        forcedPoints.addAll(java.util.List.of(points));
    }

    @Override
    public int nextIndex(int bound) {
        Integer forced = forcedIndexes.poll();
        return forced != null ? Math.min(forced, bound - 1) : super.nextIndex(bound);
    }

    @Override
    public int nextPoint() {
        Integer forced = forcedPoints.poll();
        return forced != null ? forced : super.nextPoint();
    }
}
