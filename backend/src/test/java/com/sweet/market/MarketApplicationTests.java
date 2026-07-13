package com.sweet.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import com.sweet.market.auth.security.JwtProperties;
import com.sweet.market.catalog.query.CatalogCursorProperties;
import com.sweet.market.support.IntegrationTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

class MarketApplicationTests extends IntegrationTestSupport {

    @Autowired
    private CatalogCursorProperties catalogCursorProperties;

    @Test
    void 애플리케이션_컨텍스트가_로딩된다() {
    }

    @Test
    void 기본_카탈로그_커서_설정으로_컨텍스트가_로딩된다() {
        assertThat(catalogCursorProperties.secret()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(catalogCursorProperties.maxAge()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void JWT_시크릿은_반드시_설정되어야_한다() {
        assertThatThrownBy(() -> new JwtProperties(" ", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret must be provided.");
    }

    @Test
    void JWT_시크릿은_32바이트_이상이어야_한다() {
        assertThatThrownBy(() -> new JwtProperties("short-secret", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret must be at least 32 bytes.");
    }

    @Test
    void JWT_액세스_토큰_유효_시간은_양수여야_한다() {
        assertThatThrownBy(() -> new JwtProperties("sweet-market-test-secret-key-32bytes-minimum", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT access token validity seconds must be positive.");
    }

}
