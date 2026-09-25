package com.linepay.reward.mission.repository;

import com.linepay.reward.mission.domain.MissionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionItemRepository extends JpaRepository<MissionItem, String> {

    List<MissionItem> findByMissionIdOrderByMissionItemIdAsc(String missionId);
}
