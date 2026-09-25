package com.linepay.reward.reward.repository;

import com.linepay.reward.reward.domain.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RewardRepository extends JpaRepository<Reward, Long> {

    Optional<Reward> findByParticipationId(Long participationId);
}
