package com.linepay.reward.mission.service;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.common.time.KstTime;
import com.linepay.reward.mission.domain.Mission;
import com.linepay.reward.mission.domain.MissionParticipation;
import com.linepay.reward.mission.domain.ParticipationPolicy;
import com.linepay.reward.mission.dto.AvailableMissionResponse;
import com.linepay.reward.mission.dto.ParticipationResponse;
import com.linepay.reward.mission.repository.MissionParticipationRepository;
import com.linepay.reward.mission.repository.MissionRepository;
import com.linepay.reward.user.UserValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 미션 서비스.
 * <ul>
 *     <li>특정 사용자 기준 참여 가능한 미션 목록 조회 (과제 6.3)</li>
 *     <li>미션 수행 완료 처리 → 미션 참여 이력 생성 (과제 6.1, 6.2)</li>
 * </ul>
 */
@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final UserValidator userValidator;
    private final Clock clock;

    public MissionService(MissionRepository missionRepository,
                          MissionParticipationRepository participationRepository,
                          UserValidator userValidator,
                          Clock clock) {
        this.missionRepository = missionRepository;
        this.participationRepository = participationRepository;
        this.userValidator = userValidator;
        this.clock = clock;
    }

    /**
     * 조회 시점에 해당 사용자가 실제로 참여할 수 있는 미션만 반환한다.
     */
    @Transactional(readOnly = true)
    public AvailableMissionResponse getAvailableMissions(String userId) {
        userValidator.validateExists(userId);
        LocalDateTime now = KstTime.now(clock);

        List<AvailableMissionResponse.MissionSummary> missions = missionRepository.findAllByOrderByMissionIdAsc().stream()
                .filter(mission -> checkParticipation(mission, userId, now).isEmpty())
                .map(AvailableMissionResponse.MissionSummary::from)
                .toList();

        return new AvailableMissionResponse(userId, missions);
    }

    /**
     * 미션 수행 완료 처리.
     * <p>
     * 미션 행에 비관적 락을 잡은 뒤 참여 조건을 검사하고 참여 이력을 생성한다.
     * 락을 잡은 상태에서 "검사 → 생성"이 한 번에 이뤄지므로, 같은 미션에 대한
     * 반복·동시 요청에서도 참여 조건(100회 / 일 10회 / 1시간)이 지켜진다.
     */
    @Transactional
    public ParticipationResponse completeMission(String userId, String missionId) {
        userValidator.validateExists(userId);
        Mission mission = missionRepository.findByIdForUpdate(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        LocalDateTime now = KstTime.now(clock);
        checkParticipation(mission, userId, now).ifPresent(errorCode -> {
            throw new BusinessException(errorCode);
        });

        // 완료 요청이 정상적으로 받아들여진 시점(now)을 참여 시각으로 기록 -> 재참여 판단 기준
        MissionParticipation participation =
                participationRepository.save(new MissionParticipation(missionId, userId, now));
        return ParticipationResponse.from(participation);
    }

    /** 참여 가능 조건 검사. 참여 불가 사유를 반환하며, 가능하면 empty */
    private Optional<ErrorCode> checkParticipation(Mission mission, String userId, LocalDateTime now) {
        // 기간 조건은 DB 조회 없이 먼저 판단
        if (!mission.isInEntryPeriod(now)) {
            return Optional.of(ErrorCode.MISSION_NOT_IN_PERIOD);
        }
        String missionId = mission.getMissionId();
        long totalCount = participationRepository.countByMissionId(missionId);
        long userDailyCount = participationRepository
                .countByMissionIdAndUserIdAndParticipatedDate(missionId, userId, KstTime.toDate(now));
        LocalDateTime lastParticipatedAt = participationRepository
                .findFirstByMissionIdAndUserIdOrderByParticipatedDateDescParticipatedTimeDescParticipationIdDesc(missionId, userId)
                .map(MissionParticipation::getParticipatedAt)
                .orElse(null);

        return ParticipationPolicy.check(mission, now, totalCount, userDailyCount, lastParticipatedAt);
    }
}
