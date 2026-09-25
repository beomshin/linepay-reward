package com.linepay.reward.mission.controller;

import com.linepay.reward.common.response.ApiResponse;
import com.linepay.reward.mission.dto.AvailableMissionResponse;
import com.linepay.reward.mission.dto.ParticipationResponse;
import com.linepay.reward.mission.service.MissionService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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
 * 경로 변수는 Spring MVC 내장 메서드 검증(Bean Validation)으로 검사하며,
 * 위반 시 GlobalExceptionHandler 가 400 + 영문 코드 + 한글 메시지로 응답한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/linepay/v1/mission", produces = MediaType.APPLICATION_JSON_VALUE)
public class MissionController {

    private final MissionService missionService;

    @GetMapping("/{userId}")
    public ApiResponse<AvailableMissionResponse> getAvailableMissions(
            @PathVariable @NotBlank String userId) {
        return ApiResponse.success(missionService.getAvailableMissions(userId));
    }

    @PostMapping("/{userId}/{missionId}/complete")
    public ApiResponse<ParticipationResponse> completeMission(
            @PathVariable @NotBlank String userId,
            @PathVariable @NotBlank String missionId) {
        return ApiResponse.success(missionService.completeMission(userId, missionId));
    }
}
