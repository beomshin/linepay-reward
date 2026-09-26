package com.linepay.reward.integration;

import com.github.benmanes.caffeine.cache.Cache;
import com.linepay.reward.common.config.CacheConfig;
import com.linepay.reward.mission.domain.Mission;
import com.linepay.reward.support.IntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일자별 미션 조회 캐싱 검증 (교정 5).
 * <p>
 * DB 조회 여부는 Hibernate 통계의 쿼리 실행 횟수로 확인한다. (이 테스트 클래스에서만 통계 수집)
 */
@DisplayName("[QA-H] 일자별 미션 조회 캐싱 (교정 5)")
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
class MissionCacheTest extends IntegrationTestSupport {

    private static final String TODAY = "20260901";

    @Autowired EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void clearStatistics() {
        // 캐시는 IntegrationTestSupport.resetState 에서 비운다
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    /** 일자별 미션 조회를 수행하고, 그동안 DB 로 나간 쿼리 수를 반환 */
    private long dbQueriesDuring(Runnable action) {
        long before = statistics.getQueryExecutionCount();
        action.run();
        return statistics.getQueryExecutionCount() - before;
    }

    private List<String> missionIds(String date) {
        return missionRepository.findEntryPeriodMissions(date).stream().map(Mission::getMissionId).toList();
    }

    @Test
    @DisplayName("QA-H01 같은 일자로 3번 조회하면 첫 조회만 DB 를 조회하고 이후는 캐시에서 응답한다")
    void sameDateHitsDbOnlyOnce() {
        assertThat(dbQueriesDuring(() -> missionIds(TODAY))).isEqualTo(1);
        assertThat(dbQueriesDuring(() -> missionIds(TODAY))).isZero();
        assertThat(dbQueriesDuring(() -> missionIds(TODAY))).isZero();

        assertThat(missionIds(TODAY)).containsExactly("MISSION_0002", "MISSION_0003");
        assertThat(cacheManager.getCache(CacheConfig.ENTRY_PERIOD_MISSIONS).get(TODAY)).isNotNull();
    }

    @Test
    @DisplayName("QA-H02 일자가 다르면 캐시 키가 달라 일자마다 한 번씩 DB 를 조회한다")
    void differentDateUsesDifferentKey() {
        assertThat(dbQueriesDuring(() -> missionIds(TODAY))).isEqualTo(1);
        assertThat(dbQueriesDuring(() -> missionIds("20260902"))).isEqualTo(1);
        assertThat(dbQueriesDuring(() -> missionIds("20260902"))).isZero();
    }

    @Test
    @DisplayName("QA-H03 캐시 만료 시간(TTL)은 저장 후 10분으로 설정되어 있다")
    void ttlIsConfigured() {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(CacheConfig.ENTRY_PERIOD_MISSIONS);
        Cache<Object, Object> nativeCache = cache.getNativeCache();

        assertThat(nativeCache.policy().expireAfterWrite())
                .hasValueSatisfying(policy -> assertThat(policy.getExpiresAfter()).isEqualTo(Duration.ofMinutes(10)));
    }
}
