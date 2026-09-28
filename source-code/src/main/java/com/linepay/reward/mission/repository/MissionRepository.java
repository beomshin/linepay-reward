package com.linepay.reward.mission.repository;

import com.linepay.reward.common.config.CacheConfig;
import com.linepay.reward.mission.domain.Mission;
import jakarta.persistence.LockModeType;
import org.springframework.cache.annotation.Cacheable;
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
     * <p>
     * [교정 5] 조회 일자를 키로 캐싱한다. 같은 일자의 두 번째 조회부터는 DB 를 조회하지 않고 캐시에서 응답한다.
     * 미션 데이터 변경은 캐시 만료 시간(TTL, application.yml)이 지나면 반영된다.
     * (일자 안에서 시각에 따라 달라지는 기간 판단은 캐시 밖의 참여 정책에서 하므로 일자 단위 캐싱으로 충분하다)
     */
    @Cacheable(cacheNames = CacheConfig.ENTRY_PERIOD_MISSIONS, key = "#today")
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
