package com.linepay.reward.reward.service;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import com.linepay.reward.common.number.BusinessNumberGenerator;
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

import java.io.UncheckedIOException;
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
    private final BusinessNumberGenerator numberGenerator;
    private final Clock clock;

    /**
     * 보상 지급 요청.
     * <p>
     * 참여 이력 행에 비관적 락을 잡아 같은 참여 이력에 대한 요청을 직렬화한다.
     * 따라서 반복·동시 요청이 들어와도 보상은 최대 한 번만 지급된다.
     * <p>
     * 쿠폰 API 호출이 실패하면(IO 오류·타임아웃·예상하지 못한 예외) 보상 결과를 FAILED 로 저장하고
     * {@link RewardFailedException} 을 던진다. 이 예외는 롤백하지 않으므로 실패 상태가 DB 에 남는다.
     */
    @Transactional(noRollbackFor = RewardFailedException.class)
    public RewardResponse requestReward(String userId, String participationNo) {
        log.info("[REWARD] 보상 지급 요청 시작 userId={} participationNo={}", userId, participationNo);
        userValidator.validateExists(userId);

        // 1) [DB] 참여 이력 행 락 획득 + 소유자 확인 (다른 사용자의 이력은 존재 여부를 드러내지 않도록 404)
        MissionParticipation participation = participationRepository.findByParticipationNoForUpdate(participationNo)
                .filter(p -> p.isOwnedBy(userId))
                .orElseThrow(() -> {
                    log.info("[REWARD] 참여 이력 없음 또는 소유자 불일치 userId={} participationNo={}", userId, participationNo);
                    return new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND);
                });
        log.info("[REWARD] 참여 이력 락 획득 participationNo={} missionId={}", participationNo, participation.getMissionId());

        // 2) [DB] 기존 보상 결과 조회: 참여 이력 1건당 보상 1회 (지급 완료면 거절, NO_REWARD 였으면 같은 행으로 재시도)
        Optional<Reward> existing = rewardRepository.findByParticipationNo(participationNo);
        log.info("[REWARD] 기존 보상 결과 조회 participationNo={} 상태={}", participationNo,
                existing.map(r -> r.getRewardStatus().name()).orElse("없음"));
        // 첫 요청이면 리워드번호를 채번해 새 보상 결과를 만든다. (NO_REWARD 재시도는 기존 리워드번호 유지)
        Reward reward = existing.orElseGet(() -> Reward.of(numberGenerator.nextRewardNo(KstTime.now(clock)), participation));
        if (reward.isGranted()) {
            log.info("[REWARD] 이미 보상 지급됨 participationNo={}", participationNo);
            throw new BusinessException(ErrorCode.REWARD_ALREADY_GRANTED);
        }

        // 3) 보상 선정·지급 후 [DB] 결과 저장
        LocalDateTime now = KstTime.now(clock);
        try {
            grantOrMarkNoReward(participation, reward, now);
        } catch (CouponFailureException e) {
            // [실패 내역 저장] 쿠폰 API 호출 실패(IO 오류·타임아웃·예상하지 못한 예외) → 보상 결과를 FAILED 로 저장(커밋)하고
            // 정의된 에러 코드로 응답한다. 같은 이력번호로 다시 요청할 수 있으며, 이전 요청에서 실제로 발급된 쿠폰은
            // 복구 단계(발급 결과 조회)에서 찾아 반영한다.
            reward.markFailed(e.getMessage(), now);
            rewardRepository.save(reward);
            log.error("[REWARD] 쿠폰 처리 실패로 보상 실패 저장 rewardNo={} participationNo={} 코드={} 사유={}",
                    reward.getRewardNo(), participationNo, e.getErrorCode(), e.getMessage());
            throw new RewardFailedException(e.getErrorCode(), e);
        }
        Reward saved = rewardRepository.save(reward);
        log.info("[REWARD] 보상 결과 저장 완료 rewardNo={} participationNo={} 상태={} 아이템={} 유형={} 포인트={} couponId={}",
                saved.getRewardNo(), participationNo, saved.getRewardStatus(), saved.getMissionItemId(), saved.getItemType(),
                saved.getPointAmount(), saved.getCouponId());
        return RewardResponse.from(saved);
    }

    /**
     * 보상 지급 결과 조회.
     */
    @Transactional(readOnly = true)
    public RewardResponse getReward(String userId, String participationNo) {
        log.info("[REWARD] 보상 결과 조회 시작 userId={} participationNo={}", userId, participationNo);
        userValidator.validateExists(userId);

        // [DB] 참여 이력 조회 + 소유자 확인
        participationRepository.findByParticipationNo(participationNo)
                .filter(p -> p.isOwnedBy(userId))
                .orElseThrow(() -> {
                    log.info("[REWARD] 참여 이력 없음 또는 소유자 불일치 userId={} participationNo={}", userId, participationNo);
                    return new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND);
                });

        // [DB] 보상 결과 조회
        Reward reward = rewardRepository.findByParticipationNo(participationNo)
                .orElseThrow(() -> {
                    log.info("[REWARD] 보상 요청 이력 없음 participationNo={}", participationNo);
                    return new BusinessException(ErrorCode.REWARD_NOT_FOUND);
                });
        log.info("[REWARD] 보상 결과 조회 완료 participationNo={} 상태={} 유형={}",
                participationNo, reward.getRewardStatus(), reward.getItemType());
        return RewardResponse.from(reward);
    }

    /**
     * 보상 선정 및 지급 (과제 7.3).
     */
    private void grantOrMarkNoReward(MissionParticipation participation, Reward reward, LocalDateTime now) {
        // [DB] 미션의 보상 아이템 목록 조회
        List<MissionItem> items = missionItemRepository.findByMission(participation.getMissionId());
        log.info("[REWARD] 보상 아이템 조회 missionId={} 건수={} 아이템={}", participation.getMissionId(), items.size(),
                items.stream().map(i -> i.getMissionItemId() + "(" + i.getItemType() + ")").toList());

        // [복구] 이전 요청에서 외부 쿠폰 발급은 성공했지만 결과 저장 전에 실패(롤백)한 경우,
        //        외부 시스템에 남은 발급 결과(8.3)를 찾아 그대로 반영해 쿠폰이 유실되지 않게 한다.
        /**
         * 쿠폰 발급 여부 판단을 쿠폰 API 조회로 처리되고 있다.
         * 실패 내역이 있는경우만 조회한다와 같은 정책에 따라 변경될 여지가 있는 코드
         */
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

        // 매 반복마다 후보 1개를 고르고, 쿠폰 발급이 거절되면 그 후보를 뺀다.
        // 따라서 최대 반복 횟수는 최초 후보 수로 정해진다. (조건이 바뀌어도 무한루프가 생기지 않도록 for 문 사용)
        int maxPicks = candidates.size();
        for (int pickCount = 0; pickCount < maxPicks && !candidates.isEmpty(); pickCount++) {
            MissionItem picked = randomizer.pick(candidates);
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
            CouponIssueResponse issued;
            try {
                log.info("[COUPON] 쿠폰 발급 요청 requestId={} userId={} couponTemplateId={}",
                        requestId, participation.getUserId(), picked.getCouponTemplateId());
                issued = couponClient.issueCoupon(
                        new CouponIssueRequest(requestId, participation.getUserId(), picked.getCouponTemplateId()));
            } catch (CouponApiException e) {
                if (e.getErrorType() == CouponErrorType.QUANTITY_EXHAUSTED
                        || e.getErrorType() == CouponErrorType.INVALID_TEMPLATE) {
                    log.info("[COUPON] 쿠폰 발급 거절 → 후보에서 제외 후 재선정 couponTemplateId={} 사유={}",
                            picked.getCouponTemplateId(), e.getErrorType());
                    candidates.remove(picked); // 보상 대상 항목 제외
                    continue;
                }
                throw couponFailure("쿠폰 발급", ErrorCode.COUPON_SYSTEM_ERROR, e);          // 예상하지 못한 응답
            } catch (UncheckedIOException e) {
                throw couponFailure("쿠폰 발급", ErrorCode.COUPON_COMMUNICATION_FAILED, e);  // IO 오류·타임아웃
            } catch (Exception e) {
                throw couponFailure("쿠폰 발급", ErrorCode.COUPON_SYSTEM_ERROR, e);          // 그 외 예외
            }
            log.info("[COUPON] 쿠폰 발급 성공 requestId={} couponId={}", issued.requestId(), issued.couponId());
            reward.grantCoupon(picked, issued.requestId(), issued.couponId(), now);
            return;
        }

        // 지급 가능한 보상이 하나도 없음 → 지급하지 않고 결과 반환 (재요청 가능)
        log.info("[REWARD] 지급 가능한 보상 없음 → NO_REWARD participationNo={}", participation.getParticipationNo());
        reward.markNoReward(now);
    }

    /** 쿠폰 템플릿 발급 가능 여부 (8.1). 존재하지 않는 템플릿은 발급 불가로 본다. */
    private boolean isCouponIssuable(String couponTemplateId) {
        CouponTemplateResponse template;
        try {
            template = couponClient.getCouponTemplate(couponTemplateId);
        } catch (CouponApiException e) {
            if (e.getErrorType() == CouponErrorType.TEMPLATE_NOT_FOUND) {
                log.info("[COUPON] 쿠폰 템플릿 없음 couponTemplateId={} → 후보 제외", couponTemplateId);
                return false;
            }
            throw couponFailure("쿠폰 템플릿 조회", ErrorCode.COUPON_SYSTEM_ERROR, e);
        } catch (UncheckedIOException e) {
            throw couponFailure("쿠폰 템플릿 조회", ErrorCode.COUPON_COMMUNICATION_FAILED, e);
        } catch (Exception e) {
            throw couponFailure("쿠폰 템플릿 조회", ErrorCode.COUPON_SYSTEM_ERROR, e);
        }
        log.info("[COUPON] 쿠폰 템플릿 조회 couponTemplateId={} 상태={} 발급수량={}/{} 발급가능={}", couponTemplateId,
                template.status(), template.issuedQuantity(), template.maxQuantity(), template.isIssuable());
        return template.isIssuable();
    }

    /** 외부 쿠폰 발급 결과 조회 (8.3). 없으면 empty */
    private Optional<CouponIssueResponse> findIssuedCoupon(String requestId) {
        CouponIssueResponse issued;
        try {
            issued = couponClient.getCouponIssue(requestId);
        } catch (CouponApiException e) {
            if (e.getErrorType() == CouponErrorType.ISSUE_NOT_FOUND) {
                log.info("[COUPON] 기존 발급 결과 조회 requestId={} 결과=없음", requestId);
                return Optional.empty();
            }
            throw couponFailure("쿠폰 발급 결과 조회", ErrorCode.COUPON_SYSTEM_ERROR, e);
        } catch (UncheckedIOException e) {
            throw couponFailure("쿠폰 발급 결과 조회", ErrorCode.COUPON_COMMUNICATION_FAILED, e);
        } catch (Exception e) {
            throw couponFailure("쿠폰 발급 결과 조회", ErrorCode.COUPON_SYSTEM_ERROR, e);
        }
        log.info("[COUPON] 기존 발급 결과 조회 requestId={} 결과=있음 couponId={}", requestId, issued.couponId());
        return Optional.of(issued);
    }

    /**
     * 쿠폰 API 호출 실패를 {@link CouponFailureException} 으로 만든다. (교정 4)
     * <ul>
     *     <li>{@link UncheckedIOException}: IO 오류·타임아웃(SocketTimeoutException 등) → COUPON_COMMUNICATION_FAILED</li>
     *     <li>예상하지 못한 {@link CouponApiException}·그 외 {@link Exception} → COUPON_SYSTEM_ERROR</li>
     * </ul>
     * 호출자인 {@link #requestReward} 가 받아 실패 내역(FAILED)을 저장한다.
     */
    private CouponFailureException couponFailure(String operation, ErrorCode errorCode, Exception e) {
        log.error("[COUPON] {} 실패 코드={} 사유={}", operation, errorCode, e.toString());
        return new CouponFailureException(operation, errorCode, e);
    }

    /**
     * 외부 쿠폰 발급 requestId (멱등 키).
     * 참여 이력번호 + 쿠폰 템플릿 기준으로 결정적으로 생성하므로, 같은 참여 이력의 재시도는
     * 외부 시스템에서 기존 발급 결과를 돌려받아 쿠폰이 중복 발급되지 않는다.
     */
    static String couponRequestId(MissionParticipation participation, MissionItem item) {
        return "REWARD_" + participation.getParticipationNo() + "_" + item.getCouponTemplateId();
    }
}
