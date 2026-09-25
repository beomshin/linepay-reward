package com.linepay.reward.mission.domain;

import com.linepay.reward.common.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 미션 참여 가능 조건 (과제 6.2).
 * <p>
 * "참여 가능한 미션 조회"와 "미션 수행 완료 처리"가 동일한 규칙을 사용하도록 한 곳에 모은다.
 * <ol>
 *     <li>미션별 참여 가능 기간: entry_start_at &lt;= 현재 &lt; entry_end_at</li>
 *     <li>미션별 전체 참여 횟수: 최대 100회</li>
 *     <li>사용자별 같은 미션 하루(KST) 참여 횟수: 최대 10회</li>
 *     <li>같은 미션 재참여: 직전 참여(완료 요청이 받아들여진 시점) 후 최소 1시간 경과</li>
 * </ol>
 */
public final class ParticipationPolicy {

    public static final long MAX_TOTAL_PARTICIPATION = 100;
    public static final long MAX_DAILY_PARTICIPATION_PER_USER = 10;
    public static final Duration REENTRY_INTERVAL = Duration.ofHours(1);

    private ParticipationPolicy() {
    }

    /**
     * 참여 가능 여부를 검사한다.
     *
     * @param mission             대상 미션
     * @param now                 현재 시각(KST)
     * @param totalCount          미션 전체 참여 횟수
     * @param userDailyCount      해당 사용자의 오늘(KST) 참여 횟수
     * @param lastParticipatedAt  해당 사용자의 직전 참여 시각 (없으면 null)
     * @return 참여 불가 사유. 참여 가능하면 {@link Optional#empty()}
     */
    public static Optional<ErrorCode> check(Mission mission,
                                            LocalDateTime now,
                                            long totalCount,
                                            long userDailyCount,
                                            LocalDateTime lastParticipatedAt) {
        if (!mission.isInEntryPeriod(now)) {
            return Optional.of(ErrorCode.MISSION_NOT_IN_PERIOD);
        }
        if (totalCount >= MAX_TOTAL_PARTICIPATION) {
            return Optional.of(ErrorCode.MISSION_TOTAL_LIMIT_EXCEEDED);
        }
        if (userDailyCount >= MAX_DAILY_PARTICIPATION_PER_USER) {
            return Optional.of(ErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
        }
        // 직전 참여 시각 + 1시간 <= 현재 시각 이어야 재참여 가능
        if (lastParticipatedAt != null && now.isBefore(lastParticipatedAt.plus(REENTRY_INTERVAL))) {
            return Optional.of(ErrorCode.MISSION_REENTRY_COOLDOWN);
        }
        return Optional.empty();
    }
}
