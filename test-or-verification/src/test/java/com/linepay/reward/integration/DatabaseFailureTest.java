package com.linepay.reward.integration;

import com.linepay.reward.support.IntegrationTestSupport;
import com.linepay.reward.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DB 시스템 이슈 테스트 (교정 4)
 * 사용자 조회 시 DB 연결을 얻지 못하는 상황을 재현한다.
 */
@AutoConfigureMockMvc
@DisplayName("[QA-D] DB 장애")
class DatabaseFailureTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    UserRepository failingUserRepository;

    @Test
    @DisplayName("QA-D06 DB 시스템 이슈: DB 연결 실패 → 503 DATABASE_ERROR (한글 메시지), 참여 이력 생성 안 됨")
    void databaseConnectionFailure() throws Exception {
        when(failingUserRepository.existsById(anyString()))
                .thenThrow(new CannotGetJdbcConnectionException("DB 연결을 얻지 못함"));

        mockMvc.perform(get("/linepay/v1/mission/USER_0001"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_ERROR"))
                .andExpect(jsonPath("$.msg").value("데이터 처리 중 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_ERROR"));
        assertThat(participationRepository.count()).isZero();
    }
}
