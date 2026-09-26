package com.linepay.reward.integration;

import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주요 조회 쿼리의 실행 계획(EXPLAIN) 검증 (교정 3)
 * <p>
 * Repository 의 JPQL 과 같은 조건의 SQL 을 H2 EXPLAIN 으로 실행해,
 * 설계한 인덱스를 사용하는지와 불필요한 정렬이 없는지 확인한다.
 * H2 실행 계획에서 사용 인덱스는 {@code /* PUBLIC.인덱스명 ... *}{@code /} 형태로 표시된다.
 */
@DisplayName("[QA-I] 조회 쿼리 실행 계획 (인덱스)")
class ExplainPlanTest extends IntegrationTestSupport {

    private static final Logger log = LoggerFactory.getLogger(ExplainPlanTest.class);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareData() {
        // 통계가 비어 있지 않도록 참여 이력을 조금 넣어 둔다
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 0, 0);
        for (int i = 0; i < 30; i++) {
            insertParticipation(i % 2 == 0 ? "MISSION_0002" : "MISSION_0003", "USER_000" + (i % 3 + 1), base.plusHours(i));
        }
    }

    private String explain(String sql) {
        String plan = jdbcTemplate.queryForObject("EXPLAIN " + sql, String.class).toUpperCase();
        log.info("[EXPLAIN] {}\n{}", sql, plan);
        return plan;
    }

    @Test
    @DisplayName("QA-I01 미션 전체 참여 수 → idx_participation_mission_user_datetime 사용")
    void countByMission() {
        String plan = explain("SELECT COUNT(*) FROM mission_participation WHERE mission_id = 'MISSION_0002'");
        assertThat(plan).contains("IDX_PARTICIPATION_MISSION_USER_DATETIME");
    }

    @Test
    @DisplayName("QA-I02 사용자 당일 참여 수 → idx_participation_mission_user_datetime 사용")
    void countDailyByUser() {
        String plan = explain("SELECT COUNT(*) FROM mission_participation"
                + " WHERE mission_id = 'MISSION_0002' AND user_id = 'USER_0001' AND participated_date = '20260801'");
        assertThat(plan).contains("IDX_PARTICIPATION_MISSION_USER_DATETIME");
    }

    @Test
    @DisplayName("QA-I03 사용자 직전 참여 1건 → idx_participation_mission_user_datetime 사용")
    void latestByUser() {
        String plan = explain("SELECT * FROM mission_participation"
                + " WHERE mission_id = 'MISSION_0002' AND user_id = 'USER_0001'"
                + " ORDER BY participated_date DESC, participated_time DESC FETCH FIRST 1 ROWS ONLY");
        assertThat(plan).contains("IDX_PARTICIPATION_MISSION_USER_DATETIME");
    }

    @Test
    @DisplayName("QA-I04 이력번호 단건 조회(보상 요청 락 포함) → uk_participation_no 인덱스 사용")
    void byParticipationNo() {
        String plan = explain("SELECT * FROM mission_participation WHERE participation_no = 'PT202608010000000001'");
        assertThat(plan).contains("UK_PARTICIPATION_NO");
    }

    @Test
    @DisplayName("QA-I05 이력번호로 보상 결과 조회 → uk_reward_participation_no 인덱스 사용")
    void rewardByParticipationNo() {
        String plan = explain("SELECT * FROM reward WHERE participation_no = 'PT202608010000000001'");
        assertThat(plan).contains("UK_REWARD_PARTICIPATION_NO");
    }

    @Test
    @DisplayName("QA-I06 보상 아이템 조회 → idx_mission_item_mission 사용, ORDER BY 없음 (정렬 제거)")
    void missionItems() {
        String plan = explain("SELECT * FROM mission_item WHERE mission_id = 'MISSION_0002'");
        assertThat(plan).contains("IDX_MISSION_ITEM_MISSION").doesNotContain("ORDER BY");
    }

    @Test
    @DisplayName("QA-I07 참여 기간 미션 조회 → 전체 조회 대신 기간 조건으로 idx_mission_entry_period 사용")
    void entryPeriodMissions() {
        String plan = explain("SELECT * FROM mission"
                + " WHERE entry_start_date <= '20260901' AND entry_end_date >= '20260901' ORDER BY mission_id");
        assertThat(plan).contains("IDX_MISSION_ENTRY_PERIOD")
                .contains("ENTRY_START_DATE <= '20260901'").contains("ENTRY_END_DATE >= '20260901'");
    }
}
