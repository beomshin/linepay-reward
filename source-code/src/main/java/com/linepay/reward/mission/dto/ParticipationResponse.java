package com.linepay.reward.mission.dto;

import com.linepay.reward.mission.domain.MissionParticipation;

/** 미션 수행 완료 처리 응답 (생성된 미션 참여 이력) */
public record ParticipationResponse(
        Long participationId,
        String missionId,
        String userId,
        String participatedDate,
        String participatedTime
) {
    public static ParticipationResponse from(MissionParticipation participation) {
        return new ParticipationResponse(
                participation.getParticipationId(),
                participation.getMissionId(),
                participation.getUserId(),
                participation.getParticipatedDate(),
                participation.getParticipatedTime());
    }
}
