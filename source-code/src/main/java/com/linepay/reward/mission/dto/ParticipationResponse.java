package com.linepay.reward.mission.dto;

import com.linepay.reward.mission.domain.MissionParticipation;

/**
 * 미션 수행 완료 처리 응답 (생성된 미션 참여 이력).
 * 참여 이력은 이력번호(participationNo)로 식별하며, DB PK 는 응답에 노출하지 않는다.
 */
public record ParticipationResponse(
        String participationNo,
        String missionId,
        String userId,
        String participatedDate,
        String participatedTime
) {
    public static ParticipationResponse from(MissionParticipation participation) {
        return new ParticipationResponse(
                participation.getParticipationNo(),
                participation.getMissionId(),
                participation.getUserId(),
                participation.getParticipatedDate(),
                participation.getParticipatedTime());
    }
}
