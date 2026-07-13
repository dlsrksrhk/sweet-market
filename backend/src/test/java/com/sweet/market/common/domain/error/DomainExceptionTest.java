package com.sweet.market.common.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;

class DomainExceptionTest {

    @Test
    void 도메인_예외는_오류_코드를_보존한다() {
        DomainException exception = new DomainException(TestError.INVALID);

        assertThat(exception.error()).isEqualTo(TestError.INVALID);
    }

    @Test
    void 비즈니스_예외는_원인_예외를_보존한다() {
        RuntimeException cause = new RuntimeException();

        assertThat(new BusinessException(ErrorCode.VALIDATION_ERROR, cause).getCause()).isSameAs(cause);
    }

    private enum TestError implements DomainError {
        INVALID
    }
}
