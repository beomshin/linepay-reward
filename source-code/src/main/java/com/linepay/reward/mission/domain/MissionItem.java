package com.linepay.reward.mission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 미션별 지급 가능한 보상 아이템 (Seed Data 10.3).
 * <p>
 * {@link ItemType#COUPON} 인 경우에만 외부 쿠폰 템플릿 ID 를 가진다.
 */
@Entity
@Table(name = "mission_item", indexes = @Index(name = "idx_mission_item_mission", columnList = "mission_id"))
public class MissionItem {

    @Id
    @Column(name = "mission_item_id", length = 50)
    private String missionItemId;

    @Column(name = "mission_id", nullable = false, length = 50)
    private String missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private ItemType itemType;

    @Column(name = "coupon_template_id", length = 50)
    private String couponTemplateId;

    protected MissionItem() {
    }

    public MissionItem(String missionItemId, String missionId, ItemType itemType, String couponTemplateId) {
        this.missionItemId = missionItemId;
        this.missionId = missionId;
        this.itemType = itemType;
        this.couponTemplateId = couponTemplateId;
    }

    public boolean isCoupon() {
        return itemType == ItemType.COUPON;
    }

    public String getMissionItemId() {
        return missionItemId;
    }

    public String getMissionId() {
        return missionId;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public String getCouponTemplateId() {
        return couponTemplateId;
    }
}
