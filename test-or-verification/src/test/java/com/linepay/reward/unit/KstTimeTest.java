package com.linepay.reward.unit;

import com.linepay.reward.common.time.KstTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("[QA-T] KST 시간 / 일자·시간 분리 저장 형식")
class KstTimeTest {

    @Test
    @DisplayName("QA-T01 UTC 로 주어진 시각을 KST 로 변환한다 (UTC 15:00 = 다음날 KST 00:00)")
    void convertsUtcToKst() {
        Clock utcClock = Clock.fixed(Instant.parse("2026-09-01T15:00:00Z"), ZoneOffset.UTC);

        LocalDateTime now = KstTime.now(utcClock);

        assertThat(KstTime.toDate(now)).isEqualTo("20260902");
        assertThat(KstTime.toTime(now)).isEqualTo("000000");
    }

    @Test
    @DisplayName("QA-T02 일자(yyyyMMdd)·시간(HHmmss) 문자열과 LocalDateTime 상호 변환")
    void formatAndParse() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 9, 23, 14, 30, 5);

        assertThat(KstTime.toDate(dateTime)).isEqualTo("20260923");
        assertThat(KstTime.toTime(dateTime)).isEqualTo("143005");
        assertThat(KstTime.of("20260923", "143005")).isEqualTo(dateTime);
    }

    @Test
    @DisplayName("QA-T03 저장 단위가 초이므로 나노초는 절삭한다")
    void truncatesNanos() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T03:00:00.999Z"), ZoneOffset.UTC);

        assertThat(KstTime.now(clock)).isEqualTo(LocalDateTime.of(2026, 9, 1, 12, 0, 0));
    }
}
