package com.linepay.reward.mission.controller;

import com.linepay.reward.common.response.ApiResponse;
import com.linepay.reward.mission.dto.AvailableMissionResponse;
import com.linepay.reward.mission.dto.ParticipationResponse;
import com.linepay.reward.mission.service.MissionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미션 API
 * <ul>
 *     <li>GET  /linepay/v1/mission/{userId}                     : 참여 가능한 미션 목록 조회</li>
 *     <li>POST /linepay/v1/mission/{userId}/{missionId}/complete : 미션 수행 완료 처리</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/linepay/v1/mission", produces = MediaType.APPLICATION_JSON_VALUE)
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping("/{userId}")
    public ApiResponse<AvailableMissionResponse> getAvailableMissions(@PathVariable String userId) {
        return ApiResponse.success(missionService.getAvailableMissions(userId));
    }

    @PostMapping("/{userId}/{missionId}/complete")
    public ApiResponse<ParticipationResponse> completeMission(@PathVariable String userId,
                                                              @PathVariable String missionId) {
        return ApiResponse.success(missionService.completeMission(userId, missionId));
    }
}
