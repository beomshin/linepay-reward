package com.linepay.reward.mission.domain;

import com.linepay.reward.common.time.KstTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 미션 (Seed Data 10.2).
 * <p>
 * 참여 가능 기간(entry_start_at, entry_end_at)은 일자(yyyyMMdd)/시간(HHmmss) 컬럼으로 나눠 저장한다. (KST)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "mission")
public class Mission {

    @Id
    @Column(name = "mission_id", length = 50)
    private String missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_type", nullable = false, length = 30)
    private MissionType missionType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "entry_start_date", nullable = false, length = 8)
    private String entryStartDate;

    @Column(name = "entry_start_time", nullable = false, length = 6)
    private String entryStartTime;

    @Column(name = "entry_end_date", nullable = false, length = 8)
    private String entryEndDate;

    @Column(name = "entry_end_time", nullable = false, length = 6)
    private String entryEndTime;

    public Mission(String missionId, MissionType missionType, String title,
                   String entryStartDate, String entryStartTime,
                   String entryEndDate, String entryEndTime) {
        this.missionId = missionId;
        this.missionType = missionType;
        this.title = title;
        this.entryStartDate = entryStartDate;
        this.entryStartTime = entryStartTime;
        this.entryEndDate = entryEndDate;
        this.entryEndTime = entryEndTime;
    }

    public LocalDateTime getEntryStartAt() {
        return KstTime.of(entryStartDate, entryStartTime);
    }

    public LocalDateTime getEntryEndAt() {
        return KstTime.of(entryEndDate, entryEndTime);
    }

    /**
     * 참여 가능 기간 여부 (과제 6.4).
     * <pre>entry_start_at &lt;= 현재 시각 &lt; entry_end_at</pre>
     */
    public boolean isInEntryPeriod(LocalDateTime now) {
        return !now.isBefore(getEntryStartAt()) && now.isBefore(getEntryEndAt());
    }
}
