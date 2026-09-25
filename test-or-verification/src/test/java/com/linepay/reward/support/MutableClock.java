package com.linepay.reward.support;

import com.linepay.reward.common.time.KstTime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 테스트용 가변 Clock.
 * 시간 경과(1시간 재참여, 일자 경계 등)를 테스트에서 직접 제어한다.
 */
public class MutableClock extends Clock {

    /** 과제 9절 Seed Data 검증 기준 시각 */
    public static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-09-01T12:00:00+09:00");

    private volatile Instant instant = BASE_TIME.toInstant();

    public void reset() {
        this.instant = BASE_TIME.toInstant();
    }

    /** KST 로컬 일시로 설정 */
    public void setKst(LocalDateTime kstDateTime) {
        this.instant = kstDateTime.atZone(KstTime.ZONE).toInstant();
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return KstTime.ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        // 호출 시점의 시각 스냅샷을 반환 (KstTime.now 에서 즉시 사용)
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
