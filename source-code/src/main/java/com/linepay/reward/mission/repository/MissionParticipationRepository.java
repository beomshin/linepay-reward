package com.linepay.reward.mission.repository;

import com.linepay.reward.mission.domain.MissionParticipation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 미션 참여 이력 저장소.
 * <p>
 * 교정 3: 긴 JPA 메소드명은 JPQL(@Query)로 바꾸고, 짧고 명확한 조회(countByMissionId, findByParticipationNo)는
 * JPA 메소드명 쿼리를 그대로 쓴다. 조회 조건은 모두 아래 인덱스를 타도록 설계했다.
 * <ul>
 *     <li>{@code idx_participation_mission_user_datetime} (mission_id, user_id, participated_date, participated_time)</li>
 *     <li>{@code uk_participation_no} (participation_no)</li>
 * </ul>
 */
public interface MissionParticipationRepository extends JpaRepository<MissionParticipation, Long> {

    /** 미션 전체 참여 횟수 (인덱스 선두 컬럼 mission_id) */
    long countByMissionId(String missionId);

    /** 사용자의 특정 일자(yyyyMMdd, KST) 미션 참여 횟수 (mission_id, user_id, participated_date) */
    @Query("""
            select count(p) from MissionParticipation p
             where p.missionId = :missionId
               and p.userId = :userId
               and p.participatedDate = :participatedDate
            """)
    long countDailyByUser(@Param("missionId") String missionId,
                          @Param("userId") String userId,
                          @Param("participatedDate") String participatedDate);

    /** 사용자의 해당 미션 참여 이력을 최근 순으로 조회 (인덱스 순서와 같은 일자·시간 역순) */
    @Query("""
            select p from MissionParticipation p
             where p.missionId = :missionId
               and p.userId = :userId
             order by p.participatedDate desc, p.participatedTime desc
            """)
    List<MissionParticipation> findRecentByUser(@Param("missionId") String missionId,
                                                @Param("userId") String userId);

    /** 사용자의 해당 미션 직전 참여 이력 1건 */
    default Optional<MissionParticipation> findLatestByUser(String missionId, String userId) {
        return findRecentByUser(missionId, userId).stream().findFirst();
    }

    /** 이력번호로 단건 조회 (uk_participation_no) */
    Optional<MissionParticipation> findByParticipationNo(String participationNo);

    /**
     * 이력번호로 조회하면서 행에 비관적 쓰기 락을 건다.
     * 같은 참여 이력에 대한 보상 지급 요청을 직렬화하여 중복 지급을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MissionParticipation p where p.participationNo = :participationNo")
    Optional<MissionParticipation> findByParticipationNoForUpdate(@Param("participationNo") String participationNo);
}
