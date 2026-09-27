package com.linepay.reward.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.coupon.CouponApiException;
import com.linepay.reward.coupon.CouponErrorType;
import com.linepay.reward.coupon.FakeCouponSystem;
import com.linepay.reward.mission.domain.ItemType;
import com.linepay.reward.mission.service.MissionService;
import com.linepay.reward.reward.domain.Reward;
import com.linepay.reward.reward.domain.RewardStatus;
import com.linepay.reward.reward.dto.RewardResponse;
import com.linepay.reward.reward.service.RewardService;
import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 외부 쿠폰 시스템 호출 실패 처리.
 * <ul>
 *     <li>발급 시점 거절: 템플릿 조회 때는 가능했지만 실제 발급 때 거절되는 상황(경쟁 상태)</li>
 *     <li>예상하지 못한 응답·통신 오류(IO 오류, 읽기 타임아웃)·기타 예외 → 실패 내역(FAILED) 저장 (교정 4)</li>
 * </ul>
 * 쿠폰 시스템 통신 오류는 동기 호출에서 클라이언트가 던지는 예외(IO 오류, 읽기 타임아웃)로 재현한다.
 */
@AutoConfigureMockMvc
@DisplayName("[QA-F] 쿠폰 시스템 호출 실패 처리")
class CouponFailureTest extends IntegrationTestSupport {

    private static final String COUPON_TEMPLATE = "COUPON_TEMPLATE_0001";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MissionService missionService;
    @Autowired
    RewardService rewardService;
    @MockitoSpyBean
    FakeCouponSystem couponSpy;

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
    @DisplayName("QA-F01 발급 시점에 한도 소진되면 쿠폰을 후보에서 제외하고 포인트로 다시 선정한다")
    void fallbackToPointWhenExhaustedAtIssue() {
        doThrow(new CouponApiException(CouponErrorType.QUANTITY_EXHAUSTED)).when(couponSpy).issueCoupon(any());
        String participationNo = missionService.completeMission("USER_0001", "MISSION_0002").participationNo();
        randomizer.forcePicks("ITEM_0003"); // 먼저 쿠폰 선택

        RewardResponse reward = rewardService.requestReward("USER_0001", participationNo);

        assertThat(reward.rewardStatus()).isEqualTo(RewardStatus.GRANTED);
        assertThat(reward.itemType()).isEqualTo(ItemType.REWARD_POINT);
    }

    @Test
    @DisplayName("QA-F02 예상하지 못한 외부 오류(REQUEST_ID_CONFLICT)는 COUPON_SYSTEM_ERROR, 보상 결과는 FAILED 로 저장된다")
    void unexpectedCouponError() {
        doThrow(new CouponApiException(CouponErrorType.REQUEST_ID_CONFLICT)).when(couponSpy).issueCoupon(any());
        String participationNo = missionService.completeMission("USER_0001", "MISSION_0002").participationNo();
        randomizer.forcePicks("ITEM_0003");

        assertThatThrownBy(() -> rewardService.requestReward("USER_0001", participationNo))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COUPON_SYSTEM_ERROR);
        assertThat(rewardRepository.findByParticipationNo(participationNo))
                .get().extracting("rewardStatus").isEqualTo(RewardStatus.FAILED);
    }

    @Test
    @DisplayName("QA-F03 쿠폰 발급 IO 오류 → 503 COUPON_COMMUNICATION_FAILED(한글 메시지), 보상 결과는 FAILED 로 저장(보상 처리)")
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
    @DisplayName("QA-F04 쿠폰 발급 읽기 타임아웃 → FAILED, 쿠폰 시스템 회복 후 같은 이력번호로 재요청하면 같은 리워드번호로 지급")
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
    @DisplayName("QA-F05 쿠폰 템플릿 조회 통신 오류도 같은 방식으로 처리 → 503 COUPON_COMMUNICATION_FAILED, FAILED 저장")
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
    @DisplayName("QA-F06 쿠폰 발급 결과 조회 중 예상하지 못한 예외 → 500 COUPON_SYSTEM_ERROR, FAILED 저장")
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
