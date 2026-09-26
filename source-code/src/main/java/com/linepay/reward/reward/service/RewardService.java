package com.linepay.reward.reward.service;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.common.time.KstTime;
import com.linepay.reward.coupon.CouponApiException;
import com.linepay.reward.coupon.CouponClient;
import com.linepay.reward.coupon.CouponErrorType;
import com.linepay.reward.coupon.CouponIssueRequest;
import com.linepay.reward.coupon.CouponIssueResponse;
import com.linepay.reward.coupon.CouponTemplateResponse;
import com.linepay.reward.mission.domain.MissionItem;
import com.linepay.reward.mission.domain.MissionParticipation;
import com.linepay.reward.mission.repository.MissionItemRepository;
import com.linepay.reward.mission.repository.MissionParticipationRepository;
import com.linepay.reward.reward.domain.Reward;
import com.linepay.reward.reward.dto.RewardResponse;
import com.linepay.reward.reward.repository.RewardRepository;
import com.linepay.reward.user.UserValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 보상 서비스 (과제 7절).
 * <ul>
 *     <li>미션 참여 이력 하나에 대해 하나의 보상만 지급한다.</li>
 *     <li>지급 시점에 지급 가능한 보상 아이템 중 하나를 무작위로 선택한다.</li>
 *     <li>발급 한도 소진·발급 중지 쿠폰은 선택 대상에서 제외한다.</li>
 *     <li>지급 가능한 보상이 없으면 NO_REWARD 를 반환하고, 이후 재요청을 허용한다.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RewardService {

    private final MissionParticipationRepository participationRepository;
    private final MissionItemRepository missionItemRepository;
    private final RewardRepository rewardRepository;
    private final CouponClient couponClient;
    private final RewardRandomizer randomizer;
    private final UserValidator userValidator;
    private final Clock clock;

    /**
     * 보상 지급 요청.
     * <p>
     * 참여 이력 행에 비관적 락을 잡아 같은 참여 이력에 대한 요청을 직렬화한다.
     * 따라서 반복·동시 요청이 들어와도 보상은 최대 한 번만 지급된다.
     */
    @Transactional
    public RewardResponse requestReward(String userId, Long participationId) {
        log.info("[REWARD] 보상 지급 요청 시작 userId={} participationId={}", userId, participationId);
        userValidator.validateExists(userId);

        // 1) [DB] 참여 이력 행 락 획득 + 소유자 확인 (다른 사용자의 이력은 존재 여부를 드러내지 않도록 404)
        MissionParticipation participation = participationRepository.findByIdForUpdate(participationId)
                .filter(p -> p.isOwnedBy(userId))
                .orElseThrow(() -> {
                    log.info("[REWARD] 참여 이력 없음 또는 소유자 불일치 userId={} participationId={}", userId, participationId);
                    return new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND);
                });
        log.info("[REWARD] 참여 이력 락 획득 participationId={} missionId={}", participationId, participation.getMissionId());

        // 2) [DB] 기존 보상 결과 조회: 참여 이력 1건당 보상 1회 (지급 완료면 거절, NO_REWARD 였으면 같은 행으로 재시도)
        Optional<Reward> existing = rewardRepository.findByParticipationId(participationId);
        log.info("[REWARD] 기존 보상 결과 조회 participationId={} 상태={}", participationId,
                existing.map(r -> r.getRewardStatus().name()).orElse("없음"));
        Reward reward = existing.orElseGet(() -> Reward.of(participation));
        if (reward.isGranted()) {
            log.info("[REWARD] 이미 보상 지급됨 participationId={}", participationId);
            throw new BusinessException(ErrorCode.REWARD_ALREADY_GRANTED);
        }

        // 3) 보상 선정·지급 후 [DB] 결과 저장
        grantOrMarkNoReward(participation, reward, KstTime.now(clock));
        Reward saved = rewardRepository.save(reward);
        log.info("[REWARD] 보상 결과 저장 완료 participationId={} 상태={} 아이템={} 유형={} 포인트={} couponId={}",
                participationId, saved.getRewardStatus(), saved.getMissionItemId(), saved.getItemType(),
                saved.getPointAmount(), saved.getCouponId());
        return RewardResponse.from(saved);
    }

    /**
     * 보상 지급 결과 조회.
     */
    @Transactional(readOnly = true)
    public RewardResponse getReward(String userId, Long participationId) {
        log.info("[REWARD] 보상 결과 조회 시작 userId={} participationId={}", userId, participationId);
        userValidator.validateExists(userId);

        // [DB] 참여 이력 조회 + 소유자 확인
        participationRepository.findById(participationId)
                .filter(p -> p.isOwnedBy(userId))
                .orElseThrow(() -> {
                    log.info("[REWARD] 참여 이력 없음 또는 소유자 불일치 userId={} participationId={}", userId, participationId);
                    return new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND);
                });

        // [DB] 보상 결과 조회
        Reward reward = rewardRepository.findByParticipationId(participationId)
                .orElseThrow(() -> {
                    log.info("[REWARD] 보상 요청 이력 없음 participationId={}", participationId);
                    return new BusinessException(ErrorCode.REWARD_NOT_FOUND);
                });
        log.info("[REWARD] 보상 결과 조회 완료 participationId={} 상태={} 유형={}",
                participationId, reward.getRewardStatus(), reward.getItemType());
        return RewardResponse.from(reward);
    }

    /**
     * 보상 선정 및 지급 (과제 7.3).
     */
    private void grantOrMarkNoReward(MissionParticipation participation, Reward reward, LocalDateTime now) {
        // [DB] 미션의 보상 아이템 목록 조회
        List<MissionItem> items = missionItemRepository.findByMissionIdOrderByMissionItemIdAsc(participation.getMissionId());
        log.info("[REWARD] 보상 아이템 조회 missionId={} 건수={} 아이템={}", participation.getMissionId(), items.size(),
                items.stream().map(i -> i.getMissionItemId() + "(" + i.getItemType() + ")").toList());

        // [복구] 이전 요청에서 외부 쿠폰 발급은 성공했지만 결과 저장 전에 실패(롤백)한 경우,
        //        외부 시스템에 남은 발급 결과(8.3)를 찾아 그대로 반영해 쿠폰이 유실되지 않게 한다.
        for (MissionItem item : items) {
            if (item.isCoupon()) {
                Optional<CouponIssueResponse> issued = findIssuedCoupon(couponRequestId(participation, item));
                if (issued.isPresent()) {
                    log.info("[REWARD] 이전에 발급된 쿠폰으로 복구 requestId={} couponId={}",
                            issued.get().requestId(), issued.get().couponId());
                    reward.grantCoupon(item, issued.get().requestId(), issued.get().couponId(), now);
                    return;
                }
            }
        }

        // 지급 시점에 지급 가능한 보상 아이템만 후보로 삼는다. (한도 소진·발급 중지 쿠폰 제외)
        List<MissionItem> candidates = new ArrayList<>(items.stream()
                .filter(item -> !item.isCoupon() || isCouponIssuable(item.getCouponTemplateId()))
                .toList());
        log.info("[REWARD] 지급 후보 확정 건수={} 후보={}", candidates.size(),
                candidates.stream().map(MissionItem::getMissionItemId).toList());

        while (!candidates.isEmpty()) {
            MissionItem picked = candidates.get(randomizer.nextIndex(candidates.size()));
            log.info("[REWARD] 보상 아이템 무작위 선택 아이템={} 유형={} (후보 {}건 중)",
                    picked.getMissionItemId(), picked.getItemType(), candidates.size());

            if (!picked.isCoupon()) {
                int point = randomizer.nextPoint();
                log.info("[REWARD] 포인트 지급 결정 포인트={}", point);
                reward.grantPoint(picked, point, now);
                return;
            }

            // 템플릿 조회와 발급 사이에 한도가 소진되거나 발급이 중지될 수 있으므로
            // 발급이 거절되면 해당 쿠폰을 후보에서 제외하고 다시 선정한다.
            String requestId = couponRequestId(participation, picked);
            try {
                log.info("[COUPON] 쿠폰 발급 요청 requestId={} userId={} couponTemplateId={}",
                        requestId, participation.getUserId(), picked.getCouponTemplateId());
                CouponIssueResponse issued = couponClient.issueCoupon(
                        new CouponIssueRequest(requestId, participation.getUserId(), picked.getCouponTemplateId()));
                log.info("[COUPON] 쿠폰 발급 성공 requestId={} couponId={}", issued.requestId(), issued.couponId());
                reward.grantCoupon(picked, issued.requestId(), issued.couponId(), now);
                return;
            } catch (CouponApiException e) {
                if (e.getErrorType() == CouponErrorType.QUANTITY_EXHAUSTED
                        || e.getErrorType() == CouponErrorType.INVALID_TEMPLATE) {
                    log.info("[COUPON] 쿠폰 발급 거절 → 후보에서 제외 후 재선정 couponTemplateId={} 사유={}",
                            picked.getCouponTemplateId(), e.getErrorType());
                    candidates.remove(picked);
                    continue;
                }
                log.error("[COUPON] 쿠폰 시스템 예상 외 응답 requestId={} 사유={}", requestId, e.getErrorType());
                throw new BusinessException(ErrorCode.COUPON_SYSTEM_ERROR, e);
            }
        }

        // 지급 가능한 보상이 하나도 없음 → 지급하지 않고 결과 반환 (재요청 가능)
        log.info("[REWARD] 지급 가능한 보상 없음 → NO_REWARD participationId={}", participation.getParticipationId());
        reward.markNoReward(now);
    }

    /** 쿠폰 템플릿 발급 가능 여부 (8.1). 존재하지 않는 템플릿은 발급 불가로 본다. */
    private boolean isCouponIssuable(String couponTemplateId) {
        try {
            CouponTemplateResponse template = couponClient.getCouponTemplate(couponTemplateId);
            log.info("[COUPON] 쿠폰 템플릿 조회 couponTemplateId={} 상태={} 발급수량={}/{} 발급가능={}", couponTemplateId,
                    template.status(), template.issuedQuantity(), template.maxQuantity(), template.isIssuable());
            return template.isIssuable();
        } catch (CouponApiException e) {
            if (e.getErrorType() == CouponErrorType.TEMPLATE_NOT_FOUND) {
                log.info("[COUPON] 쿠폰 템플릿 없음 couponTemplateId={} → 후보 제외", couponTemplateId);
                return false;
            }
            log.error("[COUPON] 쿠폰 템플릿 조회 실패 couponTemplateId={} 사유={}", couponTemplateId, e.getErrorType());
            throw new BusinessException(ErrorCode.COUPON_SYSTEM_ERROR, e);
        }
    }

    /** 외부 쿠폰 발급 결과 조회 (8.3). 없으면 empty */
    private Optional<CouponIssueResponse> findIssuedCoupon(String requestId) {
        try {
            CouponIssueResponse issued = couponClient.getCouponIssue(requestId);
            log.info("[COUPON] 기존 발급 결과 조회 requestId={} 결과=있음 couponId={}", requestId, issued.couponId());
            return Optional.of(issued);
        } catch (CouponApiException e) {
            if (e.getErrorType() == CouponErrorType.ISSUE_NOT_FOUND) {
                log.info("[COUPON] 기존 발급 결과 조회 requestId={} 결과=없음", requestId);
                return Optional.empty();
            }
            log.error("[COUPON] 기존 발급 결과 조회 실패 requestId={} 사유={}", requestId, e.getErrorType());
            throw new BusinessException(ErrorCode.COUPON_SYSTEM_ERROR, e);
        }
    }

    /**
     * 외부 쿠폰 발급 requestId (멱등 키).
     * 참여 이력 + 쿠폰 템플릿 기준으로 결정적으로 생성하므로, 같은 참여 이력의 재시도는
     * 외부 시스템에서 기존 발급 결과를 돌려받아 쿠폰이 중복 발급되지 않는다.
     */
    static String couponRequestId(MissionParticipation participation, MissionItem item) {
        return "REWARD_" + participation.getParticipationId() + "_" + item.getCouponTemplateId();
    }
}
