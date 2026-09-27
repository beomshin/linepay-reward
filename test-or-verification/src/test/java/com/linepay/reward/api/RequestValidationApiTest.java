package com.linepay.reward.api;

import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청값 검증 (교정 1: Spring Validation)
 * - 필수값 누락 → 400 MISSING_REQUIRED_VALUE
 * - 교정 3 이후 경로 변수는 모두 문자열 식별자(userId, missionId, participationNo)이며 필수값만 검증한다.
 * 모든 응답은 영문 코드 + 한글 메시지 + data=null 이며, 검증 실패 시 서비스 로직까지 전달되지 않는다.
 */
@AutoConfigureMockMvc
@DisplayName("[QA-V] 요청값 검증 (Spring Validation)")
class RequestValidationApiTest extends IntegrationTestSupport {

    private static final String TOO_LONG_ID = "U".repeat(51);

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("QA-V01 필수값 누락: 공백 userId → 400 MISSING_REQUIRED_VALUE")
    void missingUserId() throws Exception {
        mockMvc.perform(get("/linepay/v1/mission/{userId}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_VALUE"))
                .andExpect(jsonPath("$.msg").value("필수 요청값이 누락되었습니다. (userId)"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("QA-V02 필수값 누락: 공백 missionId → 400 MISSING_REQUIRED_VALUE, 참여 이력 생성 안 됨")
    void missingMissionId() throws Exception {
        mockMvc.perform(post("/linepay/v1/mission/USER_0001/{missionId}/complete", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_VALUE"))
                .andExpect(jsonPath("$.msg").value("필수 요청값이 누락되었습니다. (missionId)"));

        assertThat(participationRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER-0001", "USER 0001", "유저0001", "USER@0001", "USER.0001"})
    @DisplayName("QA-V03 형식 제한 없음: 특수문자·한글이 포함된 userId → 검증 통과 후 404 USER_NOT_FOUND")
    void userIdWithoutFormatRule(String userId) throws Exception {
        mockMvc.perform(get("/linepay/v1/mission/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.msg").value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("QA-V04 길이 제한 없음: 50자를 넘는 userId → 검증 통과 후 404 USER_NOT_FOUND")
    void tooLongUserId() throws Exception {
        mockMvc.perform(post("/linepay/v1/reward/{userId}/1", TOO_LONG_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("QA-V05 형식 제한 없음: 특수문자가 포함된 missionId → 404 MISSION_NOT_FOUND, 참여 이력 생성 안 됨")
    void missionIdWithoutFormatRule() throws Exception {
        mockMvc.perform(post("/linepay/v1/mission/USER_0001/{missionId}/complete", "MISSION-0002"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MISSION_NOT_FOUND"))
                .andExpect(jsonPath("$.msg").value("미션을 찾을 수 없습니다."));

        assertThat(participationRepository.count()).isZero();
    }

    @Test
    @DisplayName("QA-V06 필수값 누락: 공백 participationNo → 400 MISSING_REQUIRED_VALUE (보상 요청·조회)")
    void blankParticipationNo() throws Exception {
        mockMvc.perform(post("/linepay/v1/reward/USER_0001/{participationNo}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_VALUE"))
                .andExpect(jsonPath("$.msg").value("필수 요청값이 누락되었습니다. (participationNo)"))
                .andExpect(jsonPath("$.data").value(nullValue()));
        mockMvc.perform(get("/linepay/v1/reward/USER_0001/{participationNo}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_VALUE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "PT209901010000000001"})
    @DisplayName("QA-V07 존재하지 않는 이력번호 → 검증 통과 후 404 PARTICIPATION_NOT_FOUND")
    void unknownParticipationNo(String participationNo) throws Exception {
        mockMvc.perform(post("/linepay/v1/reward/USER_0001/{participationNo}", participationNo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTICIPATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("QA-V08 정상 발급된 이력번호로 보상 결과 조회 → 요청 전이면 404 REWARD_NOT_FOUND")
    void issuedParticipationNo() throws Exception {
        String body = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andReturn().getResponse().getContentAsString();
        String participationNo = body.replaceAll(".*\"participationNo\":\"(\\w+)\".*", "$1");

        mockMvc.perform(get("/linepay/v1/reward/USER_0001/{participationNo}", participationNo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REWARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("QA-V09 검증을 통과한 정상 형식 값은 기존 비즈니스 검증으로 이어진다 (없는 사용자 → 404)")
    void validFormatReachesService() throws Exception {
        mockMvc.perform(get("/linepay/v1/mission/USER_9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

}
