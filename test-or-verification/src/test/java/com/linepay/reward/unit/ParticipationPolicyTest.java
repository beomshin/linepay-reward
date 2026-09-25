package com.linepay.reward.unit;

import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.mission.domain.Mission;
import com.linepay.reward.mission.domain.MissionType;
import com.linepay.reward.mission.domain.ParticipationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("[QA-P] 미션 참여 가능 조건 (과제 6.2 / 6.4) 경계값")
class ParticipationPolicyTest {

    // 참여 가능 기간: 2026-05-01 00:00:00 <= now < 2027-01-01 00:00:00
    private final Mission mission = new Mission("M", MissionType.RANDOM_BOX, "t",
            "20260501", "000000", "20270101", "000000");
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 1, 12, 0, 0);

    @Test
    @DisplayName("QA-P01 모든 조건 충족 시 참여 가능")
    void allowed() {
        assertThat(ParticipationPolicy.check(mission, now, 0, 0, null)).isEmpty();
    }

    @Test
    @DisplayName("QA-P02 기간 시작 시각과 같으면 참여 가능 (entry_start_at <= now)")
    void startInclusive() {
        assertThat(ParticipationPolicy.check(mission, LocalDateTime.of(2026, 5, 1, 0, 0, 0), 0, 0, null)).isEmpty();
    }

    @Test
    @DisplayName("QA-P03 기간 시작 1초 전이면 참여 불가")
    void beforeStart() {
        assertThat(ParticipationPolicy.check(mission, LocalDateTime.of(2026, 4, 30, 23, 59, 59), 0, 0, null))
                .contains(ErrorCode.MISSION_NOT_IN_PERIOD);
    }

    @Test
    @DisplayName("QA-P04 기간 종료 시각과 같으면 참여 불가 (now < entry_end_at)")
    void endExclusive() {
        assertThat(ParticipationPolicy.check(mission, LocalDateTime.of(2027, 1, 1, 0, 0, 0), 0, 0, null))
                .contains(ErrorCode.MISSION_NOT_IN_PERIOD);
        assertThat(ParticipationPolicy.check(mission, LocalDateTime.of(2026, 12, 31, 23, 59, 59), 0, 0, null))
                .isEmpty();
    }

    @Test
    @DisplayName("QA-P05 전체 참여 99회면 가능, 100회면 불가")
    void totalLimit() {
        assertThat(ParticipationPolicy.check(mission, now, 99, 0, null)).isEmpty();
        assertThat(ParticipationPolicy.check(mission, now, 100, 0, null))
                .contains(ErrorCode.MISSION_TOTAL_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("QA-P06 사용자 당일 참여 9회면 가능, 10회면 불가")
    void dailyLimit() {
        assertThat(ParticipationPolicy.check(mission, now, 0, 9, null)).isEmpty();
        assertThat(ParticipationPolicy.check(mission, now, 0, 10, null))
                .contains(ErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("QA-P07 직전 참여 후 59분 59초면 불가, 정확히 1시간이면 가능")
    void reentryInterval() {
        assertThat(ParticipationPolicy.check(mission, now, 0, 1, now.minusMinutes(59).minusSeconds(59)))
                .contains(ErrorCode.MISSION_REENTRY_COOLDOWN);
        assertThat(ParticipationPolicy.check(mission, now, 0, 1, now.minusHours(1))).isEmpty();
    }

    @Test
    @DisplayName("QA-P08 여러 조건 위반 시 기간 → 전체 → 일별 → 재참여 순으로 사유를 반환")
    void checkOrder() {
        assertThat(ParticipationPolicy.check(mission, LocalDateTime.of(2027, 1, 1, 0, 0), 100, 10, now))
                .contains(ErrorCode.MISSION_NOT_IN_PERIOD);
        assertThat(ParticipationPolicy.check(mission, now, 100, 10, now))
                .contains(ErrorCode.MISSION_TOTAL_LIMIT_EXCEEDED);
        assertThat(ParticipationPolicy.check(mission, now, 0, 10, now))
                .contains(ErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
    }
}
