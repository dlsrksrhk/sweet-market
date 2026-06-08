package com.sweet.market.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BcryptPasswordValidatorTest {

    private final BcryptPasswordValidator validator = new BcryptPasswordValidator();

    @Test
    void allowsNullAndBlankForNotBlankValidation() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }

    @Test
    void rejectsShortPassword() {
        assertThat(validator.isValid("1234567", null)).isFalse();
    }

    @Test
    void rejectsPasswordLongerThanBcryptByteLimit() {
        String password = "가".repeat(25);

        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void allowsPasswordAtBcryptByteLimit() {
        String password = "a".repeat(72);

        assertThat(validator.isValid(password, null)).isTrue();
    }
}
