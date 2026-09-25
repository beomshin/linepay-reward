package com.linepay.reward.reward.domain;

import com.linepay.reward.common.time.KstTime;
import com.linepay.reward.mission.domain.ItemType;
import com.linepay.reward.mission.domain.MissionItem;
import com.linepay.reward.mission.domain.MissionParticipation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 미션 참여 이력에 대한 보상 지급 결과 (과제 7절).
 * <p>
 * 참여 이력 하나당 최대 한 행만 존재한다({@code participation_id} UNIQUE).
 * <ul>
 *     <li>{@link RewardStatus#GRANTED}   : 포인트 또는 쿠폰 지급 완료. 추가 지급 불가</li>
 *     <li>{@link RewardStatus#NO_REWARD} : 지급 가능한 보상이 없었음. 재요청 시 같은 행을 갱신</li>
 * </ul>
 * 리워드 포인트는 서비스 내부에서 관리하므로 지급 금액을 이 테이블에 기록한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reward", uniqueConstraints = @UniqueConstraint(name = "uk_reward_participation", columnNames = "participation_id"))
public class Reward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reward_id")
    private Long rewardId;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    @Column(name = "mission_id", nullable = false, length = 50)
    private String missionId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_status", nullable = false, length = 20)
    private RewardStatus rewardStatus;

    /** 지급된 보상 아이템 (NO_REWARD 이면 null) */
    @Column(name = "mission_item_id", length = 50)
    private String missionItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 30)
    private ItemType itemType;

    /** 지급 포인트 (REWARD_POINT 인 경우 5~10) */
    @Column(name = "point_amount")
    private Integer pointAmount;

    @Column(name = "coupon_template_id", length = 50)
    private String couponTemplateId;

    /** 외부 쿠폰 시스템이 발급한 쿠폰 ID */
    @Column(name = "coupon_id", length = 50)
    private String couponId;

    /** 외부 쿠폰 발급 요청에 사용한 requestId (멱등 키) */
    @Column(name = "coupon_request_id", length = 100)
    private String couponRequestId;

    /** 최근 처리 일자 (yyyyMMdd, KST) */
    @Column(name = "processed_date", nullable = false, length = 8)
    private String processedDate;

    /** 최근 처리 시간 (HHmmss, KST) */
    @Column(name = "processed_time", nullable = false, length = 6)
    private String processedTime;

    private Reward(MissionParticipation participation) {
        this.participationId = participation.getParticipationId();
        this.missionId = participation.getMissionId();
        this.userId = participation.getUserId();
    }

    /** 참여 이력에 대한 보상 결과 행 생성 (상태는 grant/markNoReward 로 확정) */
    public static Reward of(MissionParticipation participation) {
        return new Reward(participation);
    }

    public boolean isGranted() {
        return rewardStatus == RewardStatus.GRANTED;
    }

    /** 리워드 포인트 지급 */
    public void grantPoint(MissionItem item, int pointAmount, LocalDateTime now) {
        grant(item, now);
        this.pointAmount = pointAmount;
    }

    /** 쿠폰 지급 */
    public void grantCoupon(MissionItem item, String couponRequestId, String couponId, LocalDateTime now) {
        grant(item, now);
        this.couponTemplateId = item.getCouponTemplateId();
        this.couponRequestId = couponRequestId;
        this.couponId = couponId;
    }

    /** 지급 가능한 보상 없음 */
    public void markNoReward(LocalDateTime now) {
        this.rewardStatus = RewardStatus.NO_REWARD;
        this.missionItemId = null;
        this.itemType = null;
        this.pointAmount = null;
        this.couponTemplateId = null;
        this.couponId = null;
        this.couponRequestId = null;
        touch(now);
    }

    private void grant(MissionItem item, LocalDateTime now) {
        if (isGranted()) {
            // 서비스 계층에서 먼저 막지만, 도메인에서도 한 번 더 방어한다.
            throw new IllegalStateException("이미 보상이 지급된 참여 이력입니다. participationId=" + participationId);
        }
        this.rewardStatus = RewardStatus.GRANTED;
        this.missionItemId = item.getMissionItemId();
        this.itemType = item.getItemType();
        touch(now);
    }

    private void touch(LocalDateTime now) {
        this.processedDate = KstTime.toDate(now);
        this.processedTime = KstTime.toTime(now);
    }
}
