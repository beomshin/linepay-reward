package com.linepay.reward.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linepay.reward.coupon.FakeCouponSystem;
import com.linepay.reward.reward.domain.Reward;
import com.linepay.reward.reward.domain.RewardStatus;
import com.linepay.reward.support.IntegrationTestSupport;
import com.linepay.reward.user.UserValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실패·장애 상황 테스트 (교정 4)
 * - 애플리케이션 시스템 이슈, 참여 기간 초과, 쿠폰 API 호출 실패(IO 오류·타임아웃·기타 예외 → 실패 내역 저장)
 * - DB 시스템 이슈는 {@link DatabaseFailureTest}
 * <p>
 * 쿠폰 시스템 통신 오류는 동기 호출에서 클라이언트가 던지는 예외(IO 오류, 읽기 타임아웃)로 재현한다.
 */
@AutoConfigureMockMvc
@DisplayName("[QA-D] 실패·장애 상황")
class FailureScenarioTest extends IntegrationTestSupport {

    private static final String COUPON_TEMPLATE = "COUPON_TEMPLATE_0001";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoSpyBean
    FakeCouponSystem couponSpy;
    @MockitoSpyBean
    UserValidator userValidator;

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 쿠폰 아이템만 있는 미션에 참여하고 이력번호를 돌려준다 */
    private String completeCouponOnlyMission() throws Exception {
        String missionId = TEST_PREFIX + "COUPON_MISSION";
        createMission(missionId);
        createCouponItem(TEST_PREFIX + "ITEM_C", missionId, COUPON_TEMPLATE);
        MvcResult result = mockMvc.perform(post("/linepay/v1/mission/USER_0001/" + missionId + "/complete"))
                .andExpect(status().isOk()).andReturn();
        return body(result).path("data").path("participationNo").asText();
    }

    private static UncheckedIOException ioError() {
        return new UncheckedIOException(new IOException("Connection reset"));
    }

    /** HTTP 클라이언트의 읽기 타임아웃 초과 시 발생하는 예외 */
    private static UncheckedIOException readTimeout() {
        return new UncheckedIOException(new SocketTimeoutException("Read timed out"));
    }

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

    @Test
    @DisplayName("QA-D02 참여 기간 초과: 종료 시각(2027-01-01 00:00:00)의 완료 요청 → 409 MISSION_NOT_IN_PERIOD, 1초 전은 성공")
    void completeAfterEntryPeriod() throws Exception {
        clock.setKst(LocalDateTime.of(2027, 1, 1, 0, 0, 0));
        mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MISSION_NOT_IN_PERIOD"))
                .andExpect(jsonPath("$.msg").value("미션 참여 가능 기간이 아닙니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
        assertThat(participationRepository.count()).isZero();

        clock.setKst(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andExpect(status().isOk());
        assertThat(participationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-D03 쿠폰 발급 IO 오류 → 503 COUPON_COMMUNICATION_FAILED(한글 메시지), 보상 결과는 FAILED 로 저장(보상 처리)")
    void couponIoErrorCompensated() throws Exception {
        String participationNo = completeCouponOnlyMission();
        doThrow(ioError()).when(couponSpy).issueCoupon(any());

        mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COUPON_COMMUNICATION_FAILED"))
                .andExpect(jsonPath("$.msg").value("쿠폰 시스템과 통신하지 못해 보상을 지급하지 못했습니다. 잠시 후 다시 요청해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        // 재시도 없이 1회만 호출하고, 롤백되지 않은 실패 상태가 남아 있어야 한다
        verify(couponSpy, times(1)).issueCoupon(any());
        Reward failed = rewardRepository.findByParticipationNo(participationNo).orElseThrow();
        assertThat(failed.getRewardStatus()).isEqualTo(RewardStatus.FAILED);
        assertThat(failed.getFailureReason()).contains("쿠폰 발급");
        mockMvc.perform(get("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardStatus").value("FAILED"));
    }

    @Test
    @DisplayName("QA-D04 쿠폰 발급 읽기 타임아웃 → FAILED, 쿠폰 시스템 회복 후 같은 이력번호로 재요청하면 같은 리워드번호로 지급")
    void couponTimeoutThenRetryRequest() throws Exception {
        String participationNo = completeCouponOnlyMission();
        doThrow(readTimeout()).when(couponSpy).issueCoupon(any());

        mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COUPON_COMMUNICATION_FAILED"));
        String failedRewardNo = rewardRepository.findByParticipationNo(participationNo).orElseThrow().getRewardNo();

        // 쿠폰 시스템 회복 → 같은 이력번호로 재요청
        doCallRealMethod().when(couponSpy).issueCoupon(any());
        MvcResult retried = mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardStatus").value("GRANTED"))
                .andExpect(jsonPath("$.data.itemType").value("COUPON"))
                .andExpect(jsonPath("$.data.failureReason").value(nullValue()))
                .andReturn();

        assertThat(body(retried).path("data").path("rewardNo").asText()).isEqualTo(failedRewardNo);
        assertThat(couponSpy.getCouponTemplate(COUPON_TEMPLATE).issuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-D05 쿠폰 템플릿 조회 통신 오류도 같은 방식으로 처리 → 503 COUPON_COMMUNICATION_FAILED, FAILED 저장")
    void couponTemplateLookupError() throws Exception {
        String participationNo = completeCouponOnlyMission();
        doThrow(ioError()).when(couponSpy).getCouponTemplate(any());

        mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COUPON_COMMUNICATION_FAILED"));

        Reward failed = rewardRepository.findByParticipationNo(participationNo).orElseThrow();
        assertThat(failed.getRewardStatus()).isEqualTo(RewardStatus.FAILED);
        assertThat(failed.getFailureReason()).contains("쿠폰 템플릿 조회");
    }

    @Test
    @DisplayName("QA-D07 쿠폰 발급 결과 조회 중 예상하지 못한 예외 → 500 COUPON_SYSTEM_ERROR, FAILED 저장")
    void couponIssueLookupUnexpectedError() throws Exception {
        String participationNo = completeCouponOnlyMission();
        doThrow(new IllegalStateException("응답 파싱 실패")).when(couponSpy).getCouponIssue(any());

        mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationNo))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COUPON_SYSTEM_ERROR"))
                .andExpect(jsonPath("$.msg").value("쿠폰 발급 처리 중 오류가 발생했습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        Reward failed = rewardRepository.findByParticipationNo(participationNo).orElseThrow();
        assertThat(failed.getRewardStatus()).isEqualTo(RewardStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("쿠폰 발급 결과 조회 실패 (IllegalStateException)");
    }
}
