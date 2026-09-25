package com.linepay.reward.common.config;

import com.linepay.reward.common.time.KstTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 서비스 기준 시각(Clock) 설정.
 * <p>
 * 모든 시간 판단은 이 Clock 을 통해 이뤄지므로, 기준 시각을 고정하거나 테스트에서 교체할 수 있다.
 * <ul>
 *     <li>{@code linepay.clock.fixed-at} 값이 있으면 해당 시각으로 고정 (과제 9절 검증 기준 시각)</li>
 *     <li>값이 없으면 시스템 현재 시각(KST)을 사용</li>
 * </ul>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${linepay.clock.fixed-at:}") String fixedAt) {
        if (StringUtils.hasText(fixedAt)) {
            return Clock.fixed(OffsetDateTime.parse(fixedAt).toInstant(), KstTime.ZONE);
        }
        return Clock.system(KstTime.ZONE);
    }
}
