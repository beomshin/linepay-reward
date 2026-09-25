package com.linepay.reward.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트 전용 빈 설정: 시간과 무작위 값을 제어할 수 있는 구현으로 교체한다.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public ControllableRewardRandomizer controllableRewardRandomizer() {
        return new ControllableRewardRandomizer();
    }
}
