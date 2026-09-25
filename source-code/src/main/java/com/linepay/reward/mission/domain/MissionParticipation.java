package com.linepay.reward.mission.domain;

import com.linepay.reward.common.time.KstTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 미션 참여 이력 (과제 6.1).
 * <p>
 * 미션 수행 완료 요청이 정상적으로 받아들여지면 하나씩 생성되며, {@code participationId} 로 서로 구분된다.
 * 보상 지급 요청은 이 참여 이력 하나를 대상으로 한다.
 * 참여 시각(= 완료 요청이 받아들여진 시각)은 재참여 가능 시점 판단의 기준이 된다.
 */
@Entity
@Table(name = "mission_participation", indexes = {
        @Index(name = "idx_participation_mission_user_date", columnList = "mission_id, user_id, participated_date")
})
public class MissionParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participation_id")
    private Long participationId;

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

    protected MissionParticipation() {
    }

    public MissionParticipation(String missionId, String userId, LocalDateTime participatedAt) {
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

    public Long getParticipationId() {
        return participationId;
    }

    public String getMissionId() {
        return missionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getParticipatedDate() {
        return participatedDate;
    }

    public String getParticipatedTime() {
        return participatedTime;
    }
}
