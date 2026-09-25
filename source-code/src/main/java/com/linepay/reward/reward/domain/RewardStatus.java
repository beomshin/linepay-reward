package com.linepay.reward.reward.domain;

/** 보상 지급 결과 상태 (과제 5절 5단계) */
public enum RewardStatus {
    /** 보상 지급 완료 (포인트 또는 쿠폰). 이후 추가 지급 불가 */
    GRANTED,
    /** 지급 가능한 보상이 없어 지급하지 않음. 이후 다시 요청 가능 (과제 7.3) */
    NO_REWARD
}
