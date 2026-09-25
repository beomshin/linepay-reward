package com.linepay.reward.unit;

import com.linepay.reward.coupon.CouponApiException;
import com.linepay.reward.coupon.CouponErrorType;
import com.linepay.reward.coupon.CouponIssueRequest;
import com.linepay.reward.coupon.CouponIssueResponse;
import com.linepay.reward.coupon.CouponTemplateResponse;
import com.linepay.reward.coupon.CouponTemplateStatus;
import com.linepay.reward.coupon.FakeCouponSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[QA-C] 외부 쿠폰 시스템 계약 재현 (과제 8절)")
class FakeCouponSystemTest {

    private static final String TEMPLATE = "COUPON_TEMPLATE_0001";
    private final FakeCouponSystem coupon = new FakeCouponSystem();

    @Test
    @DisplayName("QA-C01 Seed 쿠폰 템플릿 조회 (8.1 성공 응답)")
    void getSeedTemplate() {
        CouponTemplateResponse template = coupon.getCouponTemplate(TEMPLATE);

        assertThat(template.title()).isEqualTo("10% 할인 쿠폰");
        assertThat(template.maxQuantity()).isEqualTo(100);
        assertThat(template.issuedQuantity()).isZero();
        assertThat(template.status()).isEqualTo(CouponTemplateStatus.AVAILABLE);
    }

    @Test
    @DisplayName("QA-C02 존재하지 않는 템플릿 조회 시 404")
    void templateNotFound() {
        assertThatThrownBy(() -> coupon.getCouponTemplate("NONE"))
                .isInstanceOf(CouponApiException.class)
                .extracting("httpStatus").isEqualTo(404);
    }

    @Test
    @DisplayName("QA-C03 쿠폰 1개 발급 성공 시 발급 수량 증가")
    void issue() {
        CouponIssueResponse issued = coupon.issueCoupon(new CouponIssueRequest("R1", "USER_0001", TEMPLATE));

        assertThat(issued.status()).isEqualTo("ISSUED");
        assertThat(issued.couponId()).isNotBlank();
        assertThat(coupon.getCouponTemplate(TEMPLATE).issuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-C04 같은 requestId·같은 내용 재요청 시 기존 결과 반환 (중복 발급 없음)")
    void idempotent() {
        CouponIssueResponse first = coupon.issueCoupon(new CouponIssueRequest("R1", "USER_0001", TEMPLATE));
        CouponIssueResponse second = coupon.issueCoupon(new CouponIssueRequest("R1", "USER_0001", TEMPLATE));

        assertThat(second).isEqualTo(first);
        assertThat(coupon.getCouponTemplate(TEMPLATE).issuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-C05 같은 requestId·다른 내용이면 거부")
    void conflict() {
        coupon.issueCoupon(new CouponIssueRequest("R1", "USER_0001", TEMPLATE));

        assertThatThrownBy(() -> coupon.issueCoupon(new CouponIssueRequest("R1", "USER_0002", TEMPLATE)))
                .isInstanceOf(CouponApiException.class)
                .extracting("errorType").isEqualTo(CouponErrorType.REQUEST_ID_CONFLICT);
    }

    @Test
    @DisplayName("QA-C06 발급 한도 도달 시 EXHAUSTED 로 전환되고 이후 발급은 한도 소진 결과")
    void exhausted() {
        coupon.registerTemplate("T2", "t", 2, 0, CouponTemplateStatus.AVAILABLE);
        coupon.issueCoupon(new CouponIssueRequest("A", "USER_0001", "T2"));
        coupon.issueCoupon(new CouponIssueRequest("B", "USER_0001", "T2"));

        assertThat(coupon.getCouponTemplate("T2").status()).isEqualTo(CouponTemplateStatus.EXHAUSTED);
        assertThatThrownBy(() -> coupon.issueCoupon(new CouponIssueRequest("C", "USER_0001", "T2")))
                .isInstanceOf(CouponApiException.class)
                .extracting("errorType").isEqualTo(CouponErrorType.QUANTITY_EXHAUSTED);
    }

    @Test
    @DisplayName("QA-C07 발급 중지(INACTIVE)·미존재 템플릿 발급은 유효하지 않은 템플릿 결과")
    void invalidTemplate() {
        coupon.changeStatus(TEMPLATE, CouponTemplateStatus.INACTIVE);

        assertThatThrownBy(() -> coupon.issueCoupon(new CouponIssueRequest("R1", "USER_0001", TEMPLATE)))
                .extracting("errorType").isEqualTo(CouponErrorType.INVALID_TEMPLATE);
        assertThatThrownBy(() -> coupon.issueCoupon(new CouponIssueRequest("R2", "USER_0001", "NONE")))
                .extracting("errorType").isEqualTo(CouponErrorType.INVALID_TEMPLATE);
    }

    @Test
    @DisplayName("QA-C08 발급 결과 조회: 있으면 발급 응답과 동일, 없으면 404 (8.3)")
    void getIssue() {
        CouponIssueResponse issued = coupon.issueCoupon(new CouponIssueRequest("R1", "USER_0001", TEMPLATE));

        assertThat(coupon.getCouponIssue("R1")).isEqualTo(issued);
        assertThatThrownBy(() -> coupon.getCouponIssue("NONE"))
                .extracting("httpStatus").isEqualTo(404);
    }
}
