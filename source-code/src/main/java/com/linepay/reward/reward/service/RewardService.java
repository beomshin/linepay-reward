package com.linepay.reward.reward.service;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.common.time.KstTime;
import com.linepay.reward.coupon.CouponApiException;
import com.linepay.reward.coupon.CouponClient;
import com.linepay.reward.coupon.CouponErrorType;
import com.linepay.reward.coupon.CouponIssueRequest;
import com.linepay.reward.coupon.CouponIssueResponse;
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
        userValidator.validateExists(userId);
        // 1) 참여 이력 행 락 획득 + 소유자 확인 (다른 사용자의 이력은 존재 여부를 드러내지 않도록 404)
        MissionParticipation participation = participationRepository.findByIdForUpdate(participationId)
                .filter(p -> p.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));

        // 2) 참여 이력 1건당 보상 1회: 이미 지급됐으면 거절, NO_REWARD 였으면 같은 행으로 재시도
        Reward reward = rewardRepository.findByParticipationId(participationId)
                .orElseGet(() -> Reward.of(participation));
        if (reward.isGranted()) {
            log.info("[REWARD] already granted participationId={}", participationId);
            throw new BusinessException(ErrorCode.REWARD_ALREADY_GRANTED);
        }

        // 3) 보상 선정·지급 후 결과 저장
        grantOrMarkNoReward(participation, reward, KstTime.now(clock));
        Reward saved = rewardRepository.save(reward);
        log.info("[REWARD] processed participationId={} status={} itemType={} point={} couponId={}",
                participationId, saved.getRewardStatus(), saved.getItemType(), saved.getPointAmount(), saved.getCouponId());
        return RewardResponse.from(saved);
    }

    /**
     * 보상 지급 결과 조회.
     */
    @Transactional(readOnly = true)
    public RewardResponse getReward(String userId, Long participationId) {
        userValidator.validateExists(userId);
        participationRepository.findById(participationId)
                .filter(p -> p.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));

        return rewardRepository.findByParticipationId(participationId)
                .map(RewardResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.REWARD_NOT_FOUND));
    }

    /**
     * 보상 선정 및 지급 (과제 7.3).
     */
    private void grantOrMarkNoReward(MissionParticipation participation, Reward reward, LocalDateTime now) {
        List<MissionItem> items = missionItemRepository.findByMissionIdOrderByMissionItemIdAsc(participation.getMissionId());

        // [복구] 이전 요청에서 외부 쿠폰 발급은 성공했지만 결과 저장 전에 실패(롤백)한 경우,
        //        외부 시스템에 남은 발급 결과(8.3)를 찾아 그대로 반영해 쿠폰이 유실되지 않게 한다.
        for (MissionItem item : items) {
            if (item.isCoupon()) {
                Optional<CouponIssueResponse> issued = findIssuedCoupon(couponRequestId(participation, item));
                if (issued.isPresent()) {
                    log.info("[REWARD] recovered already issued coupon requestId={}", issued.get().requestId());
                    reward.grantCoupon(item, issued.get().requestId(), issued.get().couponId(), now);
                    return;
                }
            }
        }

        // 지급 시점에 지급 가능한 보상 아이템만 후보로 삼는다. (한도 소진·발급 중지 쿠폰 제외)
        List<MissionItem> candidates = new ArrayList<>(items.stream()
                .filter(item -> !item.isCoupon() || isCouponIssuable(item.getCouponTemplateId()))
                .toList());

        while (!candidates.isEmpty()) {
            MissionItem picked = candidates.get(randomizer.nextIndex(candidates.size()));

            if (!picked.isCoupon()) {
                reward.grantPoint(picked, randomizer.nextPoint(), now);
                return;
            }

            // 템플릿 조회와 발급 사이에 한도가 소진되거나 발급이 중지될 수 있으므로
            // 발급이 거절되면 해당 쿠폰을 후보에서 제외하고 다시 선정한다.
            String requestId = couponRequestId(participation, picked);
            try {
                CouponIssueResponse issued = couponClient.issueCoupon(
                        new CouponIssueRequest(requestId, participation.getUserId(), picked.getCouponTemplateId()));
                reward.grantCoupon(picked, issued.requestId(), issued.couponId(), now);
                return;
            } catch (CouponApiException e) {
                if (e.getErrorType() == CouponErrorType.QUANTITY_EXHAUSTED
                        || e.getErrorType() == CouponErrorType.INVALID_TEMPLATE) {
                    log.info("[REWARD] coupon not issuable, exclude from candidates. template={}, reason={}",
                            picked.getCouponTemplateId(), e.getErrorType());
                    candidates.remove(picked);
                    continue;
                }
                log.error("[REWARD] unexpected coupon system result requestId={} type={}", requestId, e.getErrorType());
                throw new BusinessException(ErrorCode.COUPON_SYSTEM_ERROR, e);
            }
        }

        // 지급 가능한 보상이 하나도 없음 → 지급하지 않고 결과 반환 (재요청 가능)
        reward.markNoReward(now);
    }

    /** 쿠폰 템플릿 발급 가능 여부 (8.1). 존재하지 않는 템플릿은 발급 불가로 본다. */
    private boolean isCouponIssuable(String couponTemplateId) {
        try {
            return couponClient.getCouponTemplate(couponTemplateId).isIssuable();
        } catch (CouponApiException e) {
            if (e.getErrorType() == CouponErrorType.TEMPLATE_NOT_FOUND) {
                return false;
            }
            throw new BusinessException(ErrorCode.COUPON_SYSTEM_ERROR, e);
        }
    }

    /** 외부 쿠폰 발급 결과 조회 (8.3). 없으면 empty */
    private Optional<CouponIssueResponse> findIssuedCoupon(String requestId) {
        try {
            return Optional.of(couponClient.getCouponIssue(requestId));
        } catch (CouponApiException e) {
            if (e.getErrorType() == CouponErrorType.ISSUE_NOT_FOUND) {
                return Optional.empty();
            }
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
