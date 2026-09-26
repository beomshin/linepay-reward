package com.linepay.reward.config;

import com.linepay.reward.common.config.ClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로파일별 설정 분리 검증 (교정 2)
 * - 테스트용 설정(기준 시각 고정, H2 콘솔)은 local 에만 있고 prod 에는 없어야 한다.
 */
@DisplayName("[QA-E] 환경(프로파일) 설정 분리")
class ProfileConfigTest {

    private static final String FIXED_AT = "linepay.clock.fixed-at";
    private static final String H2_CONSOLE = "spring.h2.console.enabled";

    private PropertySource<?> load(String fileName) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(fileName, new ClassPathResource(fileName));
        return sources.get(0);
    }

    @Test
    @DisplayName("QA-E01 공통 설정(application.yml)에는 테스트용 설정이 없고, 기본 프로파일은 local")
    void commonHasNoTestSettings() throws IOException {
        PropertySource<?> common = load("application.yml");

        assertThat(common.getProperty(FIXED_AT)).isNull();
        assertThat(common.getProperty(H2_CONSOLE)).isNull();
        assertThat(common.getProperty("spring.profiles.default")).isEqualTo("local");
    }

    @Test
    @DisplayName("QA-E02 local 프로파일에만 기준 시각 고정(2026-09-01T12:00:00+09:00)과 H2 콘솔이 있다")
    void localHasTestSettings() throws IOException {
        PropertySource<?> local = load("application-local.yml");

        assertThat(String.valueOf(local.getProperty(FIXED_AT))).isEqualTo("2026-09-01T12:00:00+09:00");
        assertThat(local.getProperty(H2_CONSOLE)).isEqualTo(true);
    }

    @Test
    @DisplayName("QA-E03 dev 프로파일은 기준 시각을 고정하지 않는다")
    void devHasNoFixedClock() throws IOException {
        assertThat(load("application-dev.yml").getProperty(FIXED_AT)).isNull();
    }

    @Test
    @DisplayName("QA-E04 prod 프로파일에는 테스트용 설정이 없고(H2 콘솔 비활성), 로그 파일 경로가 있다")
    void prodHasNoTestSettings() throws IOException {
        PropertySource<?> prod = load("application-prod.yml");

        assertThat(prod.getProperty(FIXED_AT)).isNull();
        assertThat(prod.getProperty(H2_CONSOLE)).isEqualTo(false);
        assertThat(prod.getProperty("logging.file.path")).isNotNull();
    }

    @Test
    @DisplayName("QA-E05 기준 시각 설정이 없으면(prod·dev) 시스템 현재 시각을 사용한다")
    void systemClockWithoutFixedAt() {
        Clock clock = new ClockConfig().clock("");

        assertThat(Duration.between(clock.instant(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
        assertThat(clock.getZone().getId()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("QA-E06 기준 시각 설정이 있으면(local) 해당 시각으로 고정된다")
    void fixedClockWithFixedAt() {
        Clock clock = new ClockConfig().clock("2026-09-01T12:00:00+09:00");

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-09-01T03:00:00Z"));
    }
}
