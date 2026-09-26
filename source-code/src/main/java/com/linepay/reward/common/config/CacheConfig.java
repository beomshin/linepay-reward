package com.linepay.reward.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 설정 (교정 5).
 * <p>
 * 캐시 구현(Caffeine)과 만료 시간(TTL)은 application.yml 의 {@code spring.cache} 에서 설정한다.
 * <ul>
 *     <li>{@link #ENTRY_PERIOD_MISSIONS}: 일자별 참여 기간 미션 목록. 키 = 조회 일자(yyyyMMdd)</li>
 * </ul>
 */
@EnableCaching
@Configuration
public class CacheConfig {

    public static final String ENTRY_PERIOD_MISSIONS = "entryPeriodMissions";
}
