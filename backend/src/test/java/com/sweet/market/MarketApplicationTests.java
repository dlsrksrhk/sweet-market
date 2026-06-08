package com.sweet.market;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sweet.market.auth.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=sweet-market-test-secret-key-32bytes-minimum")
class MarketApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void jwtSecretMustBeProvided() {
        assertThatThrownBy(() -> new JwtProperties(" ", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret must be provided.");
    }

    @Test
    void jwtSecretMustBeAtLeast32Bytes() {
        assertThatThrownBy(() -> new JwtProperties("short-secret", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret must be at least 32 bytes.");
    }

    @Test
    void jwtAccessTokenValiditySecondsMustBePositive() {
        assertThatThrownBy(() -> new JwtProperties("sweet-market-test-secret-key-32bytes-minimum", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT access token validity seconds must be positive.");
    }

}
