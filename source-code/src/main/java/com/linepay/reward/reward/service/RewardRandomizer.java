package com.linepay.reward.reward.service;

import com.linepay.reward.mission.domain.MissionItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 보상 선정 시 사용하는 무작위 값 생성기 (과제 7.1, 7.3).
 * <p>
 * 무작위 로직을 별도 컴포넌트로 분리해 테스트에서 결과를 제어할 수 있게 한다.
 */
@Component
public class RewardRandomizer {

    public static final int POINT_MIN = 5;
    public static final int POINT_MAX = 10;

    /** 지급 가능한 보상 아이템 중 하나를 무작위로 선택 (후보 목록의 순서와 무관) */
    public MissionItem pick(List<MissionItem> candidates) {
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /** 5 이상 10 이하의 리워드 포인트 */
    public int nextPoint() {
        return ThreadLocalRandom.current().nextInt(POINT_MIN, POINT_MAX + 1);
    }
}
