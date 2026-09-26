package com.linepay.reward.mission.repository;

import com.linepay.reward.mission.domain.MissionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MissionItemRepository extends JpaRepository<MissionItem, String> {

    /**
     * 미션의 보상 아이템 목록 (인덱스 {@code idx_mission_item_mission}).
     * 보상은 무작위로 선택하므로 순서가 필요 없어 정렬하지 않는다. (교정 3: 불필요한 정렬 제거)
     */
    @Query("select i from MissionItem i where i.missionId = :missionId")
    List<MissionItem> findByMission(@Param("missionId") String missionId);
}
