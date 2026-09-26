package com.linepay.reward.mission.domain;

import com.linepay.reward.common.time.KstTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 미션 참여 이력 (과제 6.1).
 * <p>
 * 미션 수행 완료 요청이 정상적으로 받아들여지면 하나씩 생성되며, 이력번호({@code participationNo})로 서로 구분된다.
 * 보상 지급 요청은 이 참여 이력 하나를 대상으로 한다.
 * <p>
 * PK({@code participationId})는 DB 내부 식별용이며, API·로직은 모두 이력번호를 기준으로 한다.
 * <p>
 * 인덱스 (교정 3)
 * <ul>
 *     <li>{@code uk_participation_no} : 이력번호 유니크 (단건 조회·락)</li>
 *     <li>{@code idx_participation_mission_user_datetime} (mission_id, user_id, participated_date, participated_time)
 *         : 미션 전체 참여 수(선두 컬럼), 사용자 당일 참여 수, 사용자 직전 참여(일자·시간 역순 1건)를 하나의 인덱스로 처리</li>
 * </ul>
 * 참여 시각(= 완료 요청이 받아들여진 시각)은 재참여 가능 시점 판단의 기준이 된다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "mission_participation",
        uniqueConstraints = @UniqueConstraint(name = "uk_participation_no", columnNames = "participation_no"),
        indexes = @Index(name = "idx_participation_mission_user_datetime",
                columnList = "mission_id, user_id, participated_date, participated_time"))
public class MissionParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participation_id")
    private Long participationId;

    /** 이력번호 (비즈니스 키, 예: PT202609010000000001) */
    @Column(name = "participation_no", nullable = false, length = 20)
    private String participationNo;

    @Column(name = "mission_id", nullable = false, length = 50)
    private String missionId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** 참여 일자 (yyyyMMdd, KST) - 일별 참여 횟수 집계 기준 */
    @Column(name = "participated_date", nullable = false, length = 8)
    private String participatedDate;

    /** 참여 시간 (HHmmss, KST) */
    @Column(name = "participated_time", nullable = false, length = 6)
    private String participatedTime;

    public MissionParticipation(String participationNo, String missionId, String userId, LocalDateTime participatedAt) {
        this.participationNo = participationNo;
        this.missionId = missionId;
        this.userId = userId;
        this.participatedDate = KstTime.toDate(participatedAt);
        this.participatedTime = KstTime.toTime(participatedAt);
    }

    public LocalDateTime getParticipatedAt() {
        return KstTime.of(participatedDate, participatedTime);
    }

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }
}
