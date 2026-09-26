package com.linepay.reward.support;

import com.linepay.reward.mission.domain.MissionItem;
import com.linepay.reward.reward.service.RewardRandomizer;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 테스트용 무작위 생성기.
 * 값을 지정하지 않으면 실제 무작위 동작을 그대로 사용하고,
 * 지정하면 큐에 넣은 순서대로 값을 반환해 보상 선정 결과를 결정적으로 만든다.
 * <p>
 * 보상 아이템은 목록 순서(인덱스)가 아니라 아이템 ID 로 지정한다.
 * (교정 3에서 아이템 조회의 정렬을 제거해 목록 순서가 보장되지 않으므로)
 */
public class ControllableRewardRandomizer extends RewardRandomizer {

    private final ConcurrentLinkedQueue<String> forcedItemIds = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Integer> forcedPoints = new ConcurrentLinkedQueue<>();

    public void reset() {
        forcedItemIds.clear();
        forcedPoints.clear();
    }

    /** 다음 선택에서 고를 보상 아이템 ID. 후보에 없으면(제외된 경우) 실제 무작위 선택 */
    public void forcePicks(String... missionItemIds) {
        forcedItemIds.addAll(List.of(missionItemIds));
    }

    public void forcePoints(Integer... points) {
        forcedPoints.addAll(List.of(points));
    }

    @Override
    public MissionItem pick(List<MissionItem> candidates) {
        String forced = forcedItemIds.poll();
        if (forced != null) {
            for (MissionItem candidate : candidates) {
                if (candidate.getMissionItemId().equals(forced)) {
                    return candidate;
                }
            }
        }
        return super.pick(candidates);
    }

    @Override
    public int nextPoint() {
        Integer forced = forcedPoints.poll();
        return forced != null ? forced : super.nextPoint();
    }
}
