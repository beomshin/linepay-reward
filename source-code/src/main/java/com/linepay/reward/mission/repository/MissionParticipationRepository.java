package com.linepay.reward.mission.repository;

import com.linepay.reward.mission.domain.MissionParticipation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MissionParticipationRepository extends JpaRepository<MissionParticipation, Long> {

    /** 미션 전체 참여 횟수 */
    long countByMissionId(String missionId);

    /** 사용자의 특정 일자(yyyyMMdd, KST) 미션 참여 횟수 */
    long countByMissionIdAndUserIdAndParticipatedDate(String missionId, String userId, String participatedDate);

    /** 사용자의 해당 미션 직전 참여 이력 */
    Optional<MissionParticipation> findFirstByMissionIdAndUserIdOrderByParticipatedDateDescParticipatedTimeDescParticipationIdDesc(
            String missionId, String userId);

    /**
     * 참여 이력 행에 비관적 쓰기 락을 건다.
     * 같은 참여 이력에 대한 보상 지급 요청을 직렬화하여 중복 지급을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MissionParticipation p where p.participationId = :participationId")
    Optional<MissionParticipation> findByIdForUpdate(@Param("participationId") Long participationId);
}
