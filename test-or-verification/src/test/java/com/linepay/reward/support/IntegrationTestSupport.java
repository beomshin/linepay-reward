package com.linepay.reward.support;

import com.linepay.reward.coupon.FakeCouponSystem;
import com.linepay.reward.mission.domain.ItemType;
import com.linepay.reward.mission.domain.Mission;
import com.linepay.reward.mission.domain.MissionItem;
import com.linepay.reward.mission.domain.MissionParticipation;
import com.linepay.reward.mission.domain.MissionType;
import com.linepay.reward.mission.repository.MissionItemRepository;
import com.linepay.reward.mission.repository.MissionParticipationRepository;
import com.linepay.reward.mission.repository.MissionRepository;
import com.linepay.reward.reward.repository.RewardRepository;
import com.linepay.reward.user.User;
import com.linepay.reward.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

/**
 * 통합 테스트 공통 기반.
 * <p>
 * 실제 트랜잭션·락 동작을 검증하기 위해 테스트 메서드에 @Transactional 을 걸지 않고,
 * 매 테스트 전에 참여/보상 이력과 테스트용 데이터를 지워 Seed Data 상태로 되돌린다.
 */
@SpringBootTest(properties = {
        // 테스트 컨텍스트마다 독립된 In-memory DB 를 사용 (컨텍스트 간 간섭 방지)
        "spring.datasource.url=jdbc:h2:mem:test-${random.uuid};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@Import(TestConfig.class)
public abstract class IntegrationTestSupport {

    protected static final String TEST_PREFIX = "TEST_";

    @Autowired protected MutableClock clock;
    @Autowired protected ControllableRewardRandomizer randomizer;
    @Autowired protected FakeCouponSystem couponSystem;
    @Autowired protected UserRepository userRepository;
    @Autowired protected MissionRepository missionRepository;
    @Autowired protected MissionItemRepository missionItemRepository;
    @Autowired protected MissionParticipationRepository participationRepository;
    @Autowired protected RewardRepository rewardRepository;

    @BeforeEach
    void resetState() {
        rewardRepository.deleteAllInBatch();
        participationRepository.deleteAllInBatch();
        missionItemRepository.findAll().stream()
                .filter(item -> item.getMissionItemId().startsWith(TEST_PREFIX))
                .forEach(missionItemRepository::delete);
        missionRepository.findAll().stream()
                .filter(mission -> mission.getMissionId().startsWith(TEST_PREFIX))
                .forEach(missionRepository::delete);
        userRepository.findAll().stream()
                .filter(user -> user.getUserId().startsWith(TEST_PREFIX))
                .forEach(userRepository::delete);
        couponSystem.reset();
        clock.reset();
        randomizer.reset();
    }

    /** 참여 기간이 기준 시각을 포함하는 테스트 미션 생성 (2026-05-01 ~ 2027-01-01) */
    protected Mission createMission(String missionId) {
        return missionRepository.save(new Mission(missionId, MissionType.RANDOM_BOX, "테스트 미션",
                "20260501", "000000", "20270101", "000000"));
    }

    protected MissionItem createCouponItem(String itemId, String missionId, String couponTemplateId) {
        return missionItemRepository.save(new MissionItem(itemId, missionId, ItemType.COUPON, couponTemplateId));
    }

    protected User createUser(String userId) {
        return userRepository.save(new User(userId, userId));
    }

    /** 정책 검사를 거치지 않고 참여 이력을 직접 적재 (한도 경계 상황 준비용) */
    protected MissionParticipation insertParticipation(String missionId, String userId, LocalDateTime at) {
        return participationRepository.save(new MissionParticipation(missionId, userId, at));
    }
}
