package com.linepay.reward.mission.repository;

import com.linepay.reward.mission.domain.Mission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, String> {

    /**
     * 오늘(KST) 참여 기간에 걸친 미션만 조회한다. (교정 3: 전체 조회 개선)
     * <p>
     * 일자 컬럼으로 1차 필터링(인덱스 {@code idx_mission_entry_period})하고,
     * 시·분·초까지의 정확한 기간 판단({@code entry_start_at <= 현재 < entry_end_at})은 서비스의 참여 정책에서 한다.
     */
    @Query("""
            select m from Mission m
             where m.entryStartDate <= :today
               and m.entryEndDate >= :today
             order by m.missionId
            """)
    List<Mission> findEntryPeriodMissions(@Param("today") String today);

    /**
     * 미션 행에 비관적 쓰기 락(SELECT ... FOR UPDATE)을 건다.
     * <p>
     * 같은 미션에 대한 참여 완료 요청을 직렬화하여, 동시에 요청이 들어와도
     * 전체 100회 / 일 10회 / 1시간 재참여 규칙이 깨지지 않게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mission m where m.missionId = :missionId")
    Optional<Mission> findByIdForUpdate(@Param("missionId") String missionId);
}
