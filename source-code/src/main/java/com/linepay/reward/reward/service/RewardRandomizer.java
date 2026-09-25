package com.linepay.reward.reward.service;

import org.springframework.stereotype.Component;

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

    /** [0, bound) 범위의 인덱스 - 지급 가능한 보상 아이템 중 하나를 고를 때 사용 */
    public int nextIndex(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    /** 5 이상 10 이하의 리워드 포인트 */
    public int nextPoint() {
        return ThreadLocalRandom.current().nextInt(POINT_MIN, POINT_MAX + 1);
    }
}
