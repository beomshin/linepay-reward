package com.linepay.reward.mission.dto;

import com.linepay.reward.mission.domain.Mission;
import com.linepay.reward.mission.domain.MissionType;

import java.util.List;

/** 참여 가능한 미션 목록 조회 응답 */
public record AvailableMissionResponse(String userId, List<MissionSummary> missions) {

    /** 미션 요약 정보 (일자/시간 분리 표기, KST) */
    public record MissionSummary(
            String missionId,
            MissionType missionType,
            String title,
            String entryStartDate,
            String entryStartTime,
            String entryEndDate,
            String entryEndTime
    ) {
        public static MissionSummary from(Mission mission) {
            return new MissionSummary(
                    mission.getMissionId(),
                    mission.getMissionType(),
                    mission.getTitle(),
                    mission.getEntryStartDate(),
                    mission.getEntryStartTime(),
                    mission.getEntryEndDate(),
                    mission.getEntryEndTime());
        }
    }
}
