package com.linepay.reward.integration;

import com.linepay.reward.support.IntegrationTestSupport;
import com.linepay.reward.user.UserValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실패·장애 상황 테스트 (교정 4)
 * - 애플리케이션 시스템 이슈
 * - DB 시스템 이슈는 {@link DatabaseFailureTest}, 쿠폰 시스템 호출 실패는 {@link CouponFailureTest},
 *   참여 기간 초과는 {@link MissionServiceTest}
 */
@AutoConfigureMockMvc
@DisplayName("[QA-D] 실패·장애 상황")
class FailureScenarioTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;
    @MockitoSpyBean
    UserValidator userValidator;

    @Test
    @DisplayName("QA-D01 애플리케이션 시스템 이슈: 처리 중 예상하지 못한 예외 → 500 INTERNAL_SERVER_ERROR, 참여 이력 생성 안 됨")
    void applicationSystemError() throws Exception {
        doThrow(new IllegalStateException("예상하지 못한 내부 오류")).when(userValidator).validateExists(anyString());

        mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.msg").value("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(participationRepository.count()).isZero();
    }
}
