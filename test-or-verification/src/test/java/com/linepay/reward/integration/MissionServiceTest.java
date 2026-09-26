package com.linepay.reward.integration;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.mission.dto.AvailableMissionResponse;
import com.linepay.reward.mission.dto.ParticipationResponse;
import com.linepay.reward.mission.service.MissionService;
import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[QA-M] 미션 조회 / 미션 수행 완료 처리 (과제 6절)")
class MissionServiceTest extends IntegrationTestSupport {

    @Autowired
    MissionService missionService;

    private List<String> availableMissionIds(String userId) {
        return missionService.getAvailableMissions(userId).missions().stream()
                .map(AvailableMissionResponse.MissionSummary::missionId)
                .toList();
    }

    private ErrorCode errorOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
        throw new AssertionError("BusinessException 이 발생해야 합니다.");
    }

    @Test
    @DisplayName("QA-M01 기준 시각(2026-09-01 12:00 KST)에는 기간 내 미션(0002, 0003)만 조회된다")
    void availableMissionsAtBaseTime() {
        assertThat(availableMissionIds("USER_0001")).containsExactly("MISSION_0002", "MISSION_0003");
    }

    @Test
    @DisplayName("QA-M02 존재하지 않는 사용자 조회/완료 요청은 USER_NOT_FOUND")
    void unknownUser() {
        assertThat(errorOf(() -> missionService.getAvailableMissions("NO_USER"))).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(errorOf(() -> missionService.completeMission("NO_USER", "MISSION_0002"))).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("QA-M03 미션 완료 시 참여 이력이 생성되고 참여 일자/시간이 KST 로 분리 저장된다")
    void completeCreatesParticipation() {
        ParticipationResponse response = missionService.completeMission("USER_0001", "MISSION_0002");

        assertThat(response.participationNo()).isNotNull();
        assertThat(response.participatedDate()).isEqualTo("20260901");
        assertThat(response.participatedTime()).isEqualTo("120000");
        assertThat(participationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-M04 참여 이력은 요청마다 서로 다른 ID 로 구분된다")
    void participationsAreDistinguishable() {
        String first = missionService.completeMission("USER_0001", "MISSION_0002").participationNo();
        clock.advance(Duration.ofHours(1));
        String second = missionService.completeMission("USER_0001", "MISSION_0002").participationNo();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("QA-M05 기간이 끝난 미션(0001) 완료 요청은 MISSION_NOT_IN_PERIOD, 이력은 생성되지 않는다")
    void notInPeriod() {
        assertThat(errorOf(() -> missionService.completeMission("USER_0001", "MISSION_0001")))
                .isEqualTo(ErrorCode.MISSION_NOT_IN_PERIOD);
        assertThat(participationRepository.count()).isZero();
    }

    @Test
    @DisplayName("QA-M06 존재하지 않는 미션 완료 요청은 MISSION_NOT_FOUND")
    void unknownMission() {
        assertThat(errorOf(() -> missionService.completeMission("USER_0001", "MISSION_9999")))
                .isEqualTo(ErrorCode.MISSION_NOT_FOUND);
    }

    @Test
    @DisplayName("QA-M07 참여 직후에는 목록에서 제외되고, 1시간 경과 후 다시 조회된다")
    void cooldownReflectedInList() {
        missionService.completeMission("USER_0001", "MISSION_0002");

        assertThat(availableMissionIds("USER_0001")).containsExactly("MISSION_0003");
        // 다른 사용자에게는 영향 없음
        assertThat(availableMissionIds("USER_0002")).containsExactly("MISSION_0002", "MISSION_0003");

        clock.advance(Duration.ofMinutes(59).plusSeconds(59));
        assertThat(availableMissionIds("USER_0001")).containsExactly("MISSION_0003");
        assertThat(errorOf(() -> missionService.completeMission("USER_0001", "MISSION_0002")))
                .isEqualTo(ErrorCode.MISSION_REENTRY_COOLDOWN);

        clock.advance(Duration.ofSeconds(1));
        assertThat(availableMissionIds("USER_0001")).containsExactly("MISSION_0002", "MISSION_0003");
        assertThat(missionService.completeMission("USER_0001", "MISSION_0002").participationNo()).isNotNull();
    }

    @Test
    @DisplayName("QA-M08 같은 미션 하루 10회 초과 불가, KST 자정이 지나면 다시 참여 가능")
    void dailyLimitAndDayBoundary() {
        clock.setKst(LocalDateTime.of(2026, 9, 2, 0, 0, 0));
        for (int i = 0; i < 10; i++) {
            missionService.completeMission("USER_0001", "MISSION_0003");
            clock.advance(Duration.ofHours(1));
        }
        // 2026-09-02 10:00 → 11번째 요청
        assertThat(errorOf(() -> missionService.completeMission("USER_0001", "MISSION_0003")))
                .isEqualTo(ErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
        assertThat(availableMissionIds("USER_0001")).doesNotContain("MISSION_0003");

        // 2026-09-02 23:59:59 까지는 불가, 2026-09-03 00:00:00 부터 가능
        clock.setKst(LocalDateTime.of(2026, 9, 2, 23, 59, 59));
        assertThat(errorOf(() -> missionService.completeMission("USER_0001", "MISSION_0003")))
                .isEqualTo(ErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
        clock.setKst(LocalDateTime.of(2026, 9, 3, 0, 0, 0));
        assertThat(missionService.completeMission("USER_0001", "MISSION_0003").participatedDate()).isEqualTo("20260903");
    }

    @Test
    @DisplayName("QA-M09 1시간 재참여 제한은 날짜가 바뀌어도 유지된다 (23:30 참여 → 다음날 00:10 불가)")
    void cooldownAcrossMidnight() {
        clock.setKst(LocalDateTime.of(2026, 9, 1, 23, 30, 0));
        missionService.completeMission("USER_0001", "MISSION_0003");

        clock.setKst(LocalDateTime.of(2026, 9, 2, 0, 10, 0));
        assertThat(errorOf(() -> missionService.completeMission("USER_0001", "MISSION_0003")))
                .isEqualTo(ErrorCode.MISSION_REENTRY_COOLDOWN);
    }

    @Test
    @DisplayName("QA-M10 미션 전체 참여 100회 도달 시 모든 사용자에게 참여 불가·목록 제외")
    void totalLimit() {
        LocalDateTime past = LocalDateTime.of(2026, 8, 1, 0, 0, 0);
        for (int i = 0; i < 99; i++) {
            insertParticipation("MISSION_0003", "USER_0002", past.plusMinutes(i));
        }
        // 100번째 참여는 가능
        missionService.completeMission("USER_0001", "MISSION_0003");

        assertThat(participationRepository.countByMission("MISSION_0003")).isEqualTo(100);
        assertThat(errorOf(() -> missionService.completeMission("USER_0003", "MISSION_0003")))
                .isEqualTo(ErrorCode.MISSION_TOTAL_LIMIT_EXCEEDED);
        assertThat(availableMissionIds("USER_0003")).containsExactly("MISSION_0002");
    }

    @Test
    @DisplayName("QA-M11 거절된 요청은 참여 횟수에 포함되지 않는다")
    void rejectedRequestNotCounted() {
        missionService.completeMission("USER_0001", "MISSION_0002");
        for (int i = 0; i < 5; i++) {
            errorOf(() -> missionService.completeMission("USER_0001", "MISSION_0002"));
        }
        assertThat(participationRepository.countByMission("MISSION_0002")).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-M12 기간 시작 전인 미션은 조회되지 않고 시작 시각부터 조회된다")
    void missionBeforeStart() {
        clock.setKst(LocalDateTime.of(2026, 4, 30, 23, 59, 59));
        assertThat(availableMissionIds("USER_0001")).containsExactly("MISSION_0001");

        clock.setKst(LocalDateTime.of(2026, 5, 1, 0, 0, 0));
        assertThat(availableMissionIds("USER_0001")).containsExactly("MISSION_0002", "MISSION_0003");
    }
}
