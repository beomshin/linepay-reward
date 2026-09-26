package com.linepay.reward.mission.service;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.common.number.BusinessNumberGenerator;
import com.linepay.reward.common.time.KstTime;
import com.linepay.reward.mission.domain.Mission;
import com.linepay.reward.mission.domain.MissionParticipation;
import com.linepay.reward.mission.domain.ParticipationPolicy;
import com.linepay.reward.mission.dto.AvailableMissionResponse;
import com.linepay.reward.mission.dto.ParticipationResponse;
import com.linepay.reward.mission.repository.MissionParticipationRepository;
import com.linepay.reward.mission.repository.MissionRepository;
import com.linepay.reward.user.UserValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final UserValidator userValidator;
    private final BusinessNumberGenerator numberGenerator;
    private final Clock clock;

    /**
     * 조회 시점에 해당 사용자가 실제로 참여할 수 있는 미션만 반환한다.
     */
    @Transactional(readOnly = true)
    public AvailableMissionResponse getAvailableMissions(String userId) {
        LocalDateTime now = KstTime.now(clock);
        log.info("[MISSION] 참여 가능 미션 조회 시작 userId={} 기준시각={}", userId, now);
        userValidator.validateExists(userId);

        // [DB] 오늘 참여 기간에 걸친 미션만 조회(인덱스: idx_mission_entry_period) → 미션별 참여 조건 검사
        List<Mission> periodMissions = missionRepository.findEntryPeriodMissions(KstTime.toDate(now));
        log.info("[MISSION] 참여 기간 미션 조회 완료 기준일자={} 건수={}", KstTime.toDate(now), periodMissions.size());

        List<AvailableMissionResponse.MissionSummary> missions = periodMissions.stream()
                .filter(mission -> checkParticipation(mission, userId, now).isEmpty())
                .map(AvailableMissionResponse.MissionSummary::from)
                .toList();

        log.info("[MISSION] 참여 가능 미션 조회 완료 userId={} 참여가능건수={} missionIds={}", userId, missions.size(),
                missions.stream().map(AvailableMissionResponse.MissionSummary::missionId).toList());
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
        log.info("[MISSION] 미션 완료 처리 시작 userId={} missionId={}", userId, missionId);
        userValidator.validateExists(userId);

        // 1) [DB] 미션 행 락 획득: 같은 미션의 완료 요청은 여기서부터 한 건씩 순서대로 처리된다.
        Mission mission = missionRepository.findByIdForUpdate(missionId)
                .orElseThrow(() -> {
                    log.info("[MISSION] 미션 없음 missionId={}", missionId);
                    return new BusinessException(ErrorCode.MISSION_NOT_FOUND);
                });
        log.info("[MISSION] 미션 락 획득 missionId={} 참여기간={}~{}", missionId, mission.getEntryStartAt(), mission.getEntryEndAt());

        // 2) 락을 쥔 상태에서 참여 조건 검사 (기간 → 전체 100회 → 일 10회 → 1시간 재참여)
        LocalDateTime now = KstTime.now(clock);
        checkParticipation(mission, userId, now).ifPresent(errorCode -> {
            log.info("[MISSION] 참여 불가 userId={} missionId={} 사유={}", userId, missionId, errorCode);
            throw new BusinessException(errorCode);
        });

        // 3) 이력번호 채번(DB 시퀀스) → [DB] 참여 이력 저장
        //    완료 요청이 받아들여진 시각 = 재참여 판단 기준, 트랜잭션 커밋 시 락 해제
        String participationNo = numberGenerator.nextParticipationNo(now);
        MissionParticipation participation =
                participationRepository.save(new MissionParticipation(participationNo, missionId, userId, now));
        log.info("[MISSION] 참여 이력 저장 완료 userId={} missionId={} participationNo={} 참여일시={}",
                userId, missionId, participation.getParticipationNo(), now);
        return ParticipationResponse.from(participation);
    }

    /** 참여 가능 조건 검사. 참여 불가 사유를 반환하며, 가능하면 empty */
    private Optional<ErrorCode> checkParticipation(Mission mission, String userId, LocalDateTime now) {
        String missionId = mission.getMissionId();
        // 기간 조건은 DB 조회 없이 먼저 판단
        if (!mission.isInEntryPeriod(now)) {
            log.info("[MISSION] 참여 조건 검사 missionId={} userId={} 결과=MISSION_NOT_IN_PERIOD (참여기간 {}~{})",
                    missionId, userId, mission.getEntryStartAt(), mission.getEntryEndAt());
            return Optional.of(ErrorCode.MISSION_NOT_IN_PERIOD);
        }
        // [DB] 미션 전체 참여 수 / 사용자 당일 참여 수 / 사용자 직전 참여 이력 조회
        long totalCount = participationRepository.countByMission(missionId);
        long userDailyCount = participationRepository.countDailyByUser(missionId, userId, KstTime.toDate(now));
        LocalDateTime lastParticipatedAt = participationRepository.findLatestByUser(missionId, userId)
                .map(MissionParticipation::getParticipatedAt)
                .orElse(null);

        Optional<ErrorCode> result = ParticipationPolicy.check(mission, now, totalCount, userDailyCount, lastParticipatedAt);
        log.info("[MISSION] 참여 조건 검사 missionId={} userId={} 전체참여={} 당일참여={} 직전참여={} 결과={}",
                missionId, userId, totalCount, userDailyCount, lastParticipatedAt,
                result.map(Enum::name).orElse("참여가능"));
        return result;
    }
}
