package com.sweet.market.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BcryptPasswordValidatorTest {

    private final BcryptPasswordValidator validator = new BcryptPasswordValidator();

    @Test
    void null과_빈_문자열은_NotBlank_검증에_맡긴다() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }

    @Test
    void 짧은_비밀번호는_거부한다() {
        assertThat(validator.isValid("1234567", null)).isFalse();
    }

    @Test
    void BCrypt_바이트_제한을_초과한_비밀번호는_거부한다() {
        String password = "가".repeat(25);

        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void BCrypt_바이트_제한과_같은_비밀번호는_허용한다() {
        String password = "a".repeat(72);

        assertThat(validator.isValid(password, null)).isTrue();
    }
}
