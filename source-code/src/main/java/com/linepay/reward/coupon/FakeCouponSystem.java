package com.linepay.reward.coupon;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 외부 쿠폰 시스템 재현체 (In-memory).
 * <p>
 * 과제 8절에 따라 별도 API 서버/HTTP 통신 없이 계약에 정의된 동작만 재현한다.
 * <ul>
 *     <li>쿠폰 템플릿 조회: 없으면 404(TEMPLATE_NOT_FOUND)</li>
 *     <li>쿠폰 발급: 성공 / 발급 한도 소진 / 유효하지 않은 템플릿</li>
 *     <li>동일 requestId + 같은 내용 → 기존 발급 결과 반환 (멱등)</li>
 *     <li>동일 requestId + 다른 내용 → 거부 (REQUEST_ID_CONFLICT)</li>
 *     <li>발급 결과 조회: 없으면 404(ISSUE_NOT_FOUND)</li>
 * </ul>
 * 외부 시스템 한 대를 흉내 내므로 모든 상태 변경은 synchronized 로 원자적으로 처리한다.
 * 쿠폰 템플릿 Seed Data(10.4)는 이 재현체가 소유한다.
 */
@Component
public class FakeCouponSystem implements CouponClient {

    /** couponTemplateId -> 템플릿 상태 */
    private final Map<String, Template> templates = new HashMap<>();
    /** requestId -> 발급 이력 */
    private final Map<String, Issue> issues = new HashMap<>();
    private long couponSequence;

    public FakeCouponSystem() {
        reset();
    }

    /** Seed Data(10.4) 상태로 초기화한다. (테스트 격리용으로도 사용) */
    public synchronized void reset() {
        templates.clear();
        issues.clear();
        couponSequence = 0;
        registerTemplate("COUPON_TEMPLATE_0001", "10% 할인 쿠폰", 100, 0, CouponTemplateStatus.AVAILABLE);
    }

    /** 쿠폰 템플릿 등록/덮어쓰기 (외부 시스템 운영 상황 재현용) */
    public synchronized void registerTemplate(String couponTemplateId, String title,
                                              int maxQuantity, int issuedQuantity,
                                              CouponTemplateStatus status) {
        templates.put(couponTemplateId, new Template(couponTemplateId, title, maxQuantity, issuedQuantity, status));
    }

    /** 쿠폰 템플릿 상태 변경 (예: 발급 중지 재현) */
    public synchronized void changeStatus(String couponTemplateId, CouponTemplateStatus status) {
        Template template = templates.get(couponTemplateId);
        if (template == null) {
            throw new CouponApiException(CouponErrorType.TEMPLATE_NOT_FOUND);
        }
        template.status = status;
    }

    @Override
    public synchronized CouponTemplateResponse getCouponTemplate(String couponTemplateId) {
        Template template = templates.get(couponTemplateId);
        if (template == null) {
            throw new CouponApiException(CouponErrorType.TEMPLATE_NOT_FOUND);
        }
        return template.toResponse();
    }

    @Override
    public synchronized CouponIssueResponse issueCoupon(CouponIssueRequest request) {
        // 1) 멱등 처리: 같은 requestId 가 이미 있으면 내용 비교
        Issue existing = issues.get(request.requestId());
        if (existing != null) {
            if (existing.isSameContent(request)) {
                return existing.toResponse();
            }
            throw new CouponApiException(CouponErrorType.REQUEST_ID_CONFLICT);
        }

        // 2) 템플릿 유효성 (미존재·발급 중지 → 유효하지 않은 템플릿)
        Template template = templates.get(request.couponTemplateId());
        if (template == null || template.status == CouponTemplateStatus.INACTIVE) {
            throw new CouponApiException(CouponErrorType.INVALID_TEMPLATE);
        }

        // 3) 발급 한도
        if (template.status == CouponTemplateStatus.EXHAUSTED || template.issuedQuantity >= template.maxQuantity) {
            template.status = CouponTemplateStatus.EXHAUSTED;
            throw new CouponApiException(CouponErrorType.QUANTITY_EXHAUSTED);
        }

        // 4) 발급 (한 번에 1개)
        template.issuedQuantity++;
        if (template.issuedQuantity >= template.maxQuantity) {
            template.status = CouponTemplateStatus.EXHAUSTED;
        }
        String couponId = String.format("COUPON_%04d", ++couponSequence);
        Issue issue = new Issue(request.requestId(), request.userId(), request.couponTemplateId(), couponId);
        issues.put(request.requestId(), issue);
        return issue.toResponse();
    }

    @Override
    public synchronized CouponIssueResponse getCouponIssue(String requestId) {
        Issue issue = issues.get(requestId);
        if (issue == null) {
            throw new CouponApiException(CouponErrorType.ISSUE_NOT_FOUND);
        }
        return issue.toResponse();
    }

    /** 내부 템플릿 상태 (가변) */
    private static final class Template {
        private final String couponTemplateId;
        private final String title;
        private final int maxQuantity;
        private int issuedQuantity;
        private CouponTemplateStatus status;

        private Template(String couponTemplateId, String title, int maxQuantity, int issuedQuantity,
                         CouponTemplateStatus status) {
            this.couponTemplateId = couponTemplateId;
            this.title = title;
            this.maxQuantity = maxQuantity;
            this.issuedQuantity = issuedQuantity;
            this.status = status;
        }

        private CouponTemplateResponse toResponse() {
            return new CouponTemplateResponse(couponTemplateId, title, maxQuantity, issuedQuantity, status);
        }
    }

    /** 발급 이력 (불변) */
    private record Issue(String requestId, String userId, String couponTemplateId, String couponId) {

        private boolean isSameContent(CouponIssueRequest request) {
            return Objects.equals(userId, request.userId())
                    && Objects.equals(couponTemplateId, request.couponTemplateId());
        }

        private CouponIssueResponse toResponse() {
            return new CouponIssueResponse(requestId, couponId, CouponIssueResponse.ISSUED);
        }
    }
}
