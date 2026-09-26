package com.linepay.reward.reward.repository;

import com.linepay.reward.reward.domain.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RewardRepository extends JpaRepository<Reward, Long> {

    /** 참여 이력번호로 보상 결과 조회 (유니크 인덱스 {@code uk_reward_participation_no}) */
    Optional<Reward> findByParticipationNo(String participationNo);
}
