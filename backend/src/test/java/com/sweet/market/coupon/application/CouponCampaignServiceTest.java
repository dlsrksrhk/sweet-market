package com.sweet.market.coupon.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.domain.CouponDomainError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CouponCampaignServiceTest {

    @Test
    void 발급한도_초과는_충돌_오류코드로_변환한다() {
        CouponCampaignService service = new CouponCampaignService(null, null, null);

        BusinessException exception = ReflectionTestUtils.invokeMethod(
                service, "map", new DomainException(CouponDomainError.ISSUE_LIMIT_EXCEEDED));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED);
        assertThat(exception.errorCode().status()).isEqualTo(HttpStatus.CONFLICT);
    }
}
