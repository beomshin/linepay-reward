package com.linepay.reward.reward.controller;

import com.linepay.reward.common.response.ApiResponse;
import com.linepay.reward.reward.dto.RewardResponse;
import com.linepay.reward.reward.service.RewardService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보상 API
 * <ul>
 *     <li>POST /linepay/v1/reward/{userId}/{participationNo} : 보상 지급 요청</li>
 *     <li>GET  /linepay/v1/reward/{userId}/{participationNo} : 보상 지급 결과 조회</li>
 * </ul>
 * 참여 이력은 DB PK 가 아닌 이력번호(participationNo, 예: PT202609010000000001)로 지정한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/linepay/v1/reward", produces = MediaType.APPLICATION_JSON_VALUE)
public class RewardController {

    private final RewardService rewardService;

    @PostMapping("/{userId}/{participationNo}")
    public ApiResponse<RewardResponse> requestReward(
            @PathVariable @NotBlank String userId,
            @PathVariable @NotBlank String participationNo) {
        return ApiResponse.success(rewardService.requestReward(userId, participationNo));
    }

    @GetMapping("/{userId}/{participationNo}")
    public ApiResponse<RewardResponse> getReward(
            @PathVariable @NotBlank String userId,
            @PathVariable @NotBlank String participationNo) {
        return ApiResponse.success(rewardService.getReward(userId, participationNo));
    }
}
