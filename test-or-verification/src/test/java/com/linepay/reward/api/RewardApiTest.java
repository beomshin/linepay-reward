package com.linepay.reward.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 규격 검증 (프롬프트 4.1 / 4.2)
 * - Base Path /linepay/v1/{mission|reward}, 응답 { code, msg, data }, 실패 시 data = null
 * - 결과에 맞는 HTTP 상태 코드
 */
@AutoConfigureMockMvc
@DisplayName("[QA-A] API 규격 / 비즈니스 시나리오 E2E")
class RewardApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("QA-A01 비즈니스 시나리오 1~5단계: 조회 → 완료 → 보상 요청 → 결과 확인")
    void businessScenario() throws Exception {
        // 1. 참여 가능한 미션 조회
        mockMvc.perform(get("/linepay/v1/mission/USER_0001"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.msg").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").value("USER_0001"))
                .andExpect(jsonPath("$.data.missions.length()").value(2))
                .andExpect(jsonPath("$.data.missions[0].missionId").value("MISSION_0002"))
                .andExpect(jsonPath("$.data.missions[0].entryStartDate").value("20260501"))
                .andExpect(jsonPath("$.data.missions[0].entryStartTime").value("000000"));

        // 2~3. 미션 수행 완료 요청 → 참여 이력 생성
        MvcResult completed = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.missionId").value("MISSION_0003"))
                .andExpect(jsonPath("$.data.participationNo").value(org.hamcrest.Matchers.matchesPattern("PT20260901\\d{10}")))
                .andExpect(jsonPath("$.data.participatedDate").value("20260901"))
                .andExpect(jsonPath("$.data.participatedTime").value("120000"))
                .andReturn();
        String participationNo = body(completed).path("data").path("participationNo").asText();

        // 4. 보상 지급 요청
        mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardStatus").value("GRANTED"))
                .andExpect(jsonPath("$.data.rewardNo").value(org.hamcrest.Matchers.matchesPattern("RW20260901\\d{10}")))
                .andExpect(jsonPath("$.data.itemType").value("REWARD_POINT"))
                .andExpect(jsonPath("$.data.pointAmount").value(anyOf(is(5), is(6), is(7), is(8), is(9), is(10))));

        // 5. 보상 지급 결과 확인
        mockMvc.perform(get("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participationNo").value(participationNo))
                .andExpect(jsonPath("$.data.rewardStatus").value("GRANTED"));
    }

    @Test
    @DisplayName("QA-A02 응답 Content-Type 은 application/json; charset=UTF-8")
    void contentTypeCharset() throws Exception {
        MvcResult result = mockMvc.perform(get("/linepay/v1/mission/USER_0001")).andReturn();

        assertThat(result.getResponse().getContentType()).containsIgnoringCase("application/json")
                .containsIgnoringCase("charset=UTF-8");
    }

    @Test
    @DisplayName("QA-A03 404: 존재하지 않는 사용자/미션/참여 이력 → code=영문 사유 코드, msg=한글, data=null")
    void notFoundErrors() throws Exception {
        MvcResult userNotFound = mockMvc.perform(get("/linepay/v1/mission/NO_USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.msg").value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andReturn();
        // data 필드가 생략되지 않고 null 로 명시되어야 한다
        JsonNode json = body(userNotFound);
        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").isNull()).isTrue();

        mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_9999/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MISSION_NOT_FOUND"));

        mockMvc.perform(post("/linepay/v1/reward/USER_0001/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTICIPATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("QA-A04 409: 기간 외 미션·재참여 제한·중복 보상 → 409")
    void conflictErrors() throws Exception {
        mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0001/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MISSION_NOT_IN_PERIOD"))
                .andExpect(jsonPath("$.msg").value("미션 참여 가능 기간이 아닙니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        MvcResult completed = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete")).andReturn();
        String participationNo = body(completed).path("data").path("participationNo").asText();

        mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MISSION_REENTRY_COOLDOWN"));

        mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo)).andExpect(status().isOk());
        mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REWARD_ALREADY_GRANTED"));
    }

    @Test
    @DisplayName("QA-A05 보상 요청 전 결과 조회 → 404 REWARD_NOT_FOUND")
    void rewardNotFound() throws Exception {
        MvcResult completed = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete")).andReturn();
        String participationNo = body(completed).path("data").path("participationNo").asText();

        mockMvc.perform(get("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REWARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("QA-A06 정의되지 않은 경로(404)·허용되지 않은 메서드(405)도 공통 포맷으로 응답")
    void unknownPathAndMethod() throws Exception {
        mockMvc.perform(get("/linepay/v2/mission/USER_0001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_NOT_FOUND"));

        mockMvc.perform(get("/linepay/v1/mission/USER_0001/MISSION_0002/complete"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("QA-A07 NO_REWARD 는 오류가 아닌 정상 결과(200)로 반환된다")
    void noRewardIsSuccess() throws Exception {
        String missionId = TEST_PREFIX + "COUPON_MISSION";
        createMission(missionId);
        createCouponItem(TEST_PREFIX + "ITEM_C", missionId, "COUPON_TEMPLATE_UNKNOWN");
        MvcResult completed = mockMvc.perform(post("/linepay/v1/mission/USER_0001/" + missionId + "/complete")).andReturn();
        String participationNo = body(completed).path("data").path("participationNo").asText();

        mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.rewardStatus").value("NO_REWARD"))
                .andExpect(jsonPath("$.data.itemType").value(nullValue()));
    }
}
