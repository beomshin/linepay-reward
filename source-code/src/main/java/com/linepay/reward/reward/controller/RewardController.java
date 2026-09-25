package com.linepay.reward.reward.controller;

import com.linepay.reward.common.response.ApiResponse;
import com.linepay.reward.reward.dto.RewardResponse;
import com.linepay.reward.reward.service.RewardService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보상 API
 * <ul>
 *     <li>POST /linepay/v1/reward/{userId}/{participationId} : 보상 지급 요청</li>
 *     <li>GET  /linepay/v1/reward/{userId}/{participationId} : 보상 지급 결과 조회</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/linepay/v1/reward", produces = MediaType.APPLICATION_JSON_VALUE)
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @PostMapping("/{userId}/{participationId}")
    public ApiResponse<RewardResponse> requestReward(@PathVariable String userId,
                                                     @PathVariable Long participationId) {
        return ApiResponse.success(rewardService.requestReward(userId, participationId));
    }

    @GetMapping("/{userId}/{participationId}")
    public ApiResponse<RewardResponse> getReward(@PathVariable String userId,
                                                 @PathVariable Long participationId) {
        return ApiResponse.success(rewardService.getReward(userId, participationId));
    }
}
