package com.linepay.reward.common.number;

import com.linepay.reward.common.time.KstTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 비즈니스 키(이력번호·리워드번호) 채번기.
 * <p>
 * 형식: {@code 접두어(2) + 일자 yyyyMMdd(8) + DB 시퀀스 10자리} = 20자
 * <ul>
 *     <li>이력번호(미션 참여 이력) : {@code PT202609010000000001}</li>
 *     <li>리워드번호(보상 결과)     : {@code RW202609010000000001}</li>
 * </ul>
 * 일련번호는 DB 시퀀스({@code schema.sql})에서 받는다. 시퀀스는 동시에 여러 요청이 와도
 * 같은 값을 두 번 주지 않으므로 애플리케이션 락 없이 중복 없는 번호를 만들 수 있다.
 * 테이블에는 유니크 제약조건을 함께 두어 DB에서도 한 번 더 막는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class BusinessNumberGenerator {

    static final String PARTICIPATION_PREFIX = "PT";
    static final String REWARD_PREFIX = "RW";
    private static final String PARTICIPATION_SEQUENCE = "participation_no_seq";
    private static final String REWARD_SEQUENCE = "reward_no_seq";

    private final JdbcTemplate jdbcTemplate;

    /** 미션 참여 이력번호 채번 */
    public String nextParticipationNo(LocalDateTime now) {
        return generate(PARTICIPATION_PREFIX, PARTICIPATION_SEQUENCE, now);
    }

    /** 리워드번호 채번 */
    public String nextRewardNo(LocalDateTime now) {
        return generate(REWARD_PREFIX, REWARD_SEQUENCE, now);
    }

    private String generate(String prefix, String sequence, LocalDateTime now) {
        // [DB] 시퀀스 다음 값 조회 (시퀀스 이름은 상수만 사용하므로 SQL 주입 위험 없음)
        Long next = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR " + sequence, Long.class);
        String number = prefix + KstTime.toDate(now) + String.format("%010d", next);
        log.info("[NUMBER] 채번 완료 시퀀스={} 번호={}", sequence, number);
        return number;
    }
}
