package com.sweet.market.catalog.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCursorCodecTest {

    private static final Instant NOW = Instant.parse("2026-07-13T01:00:00Z");

    private CatalogCursorCodec codec;

    @BeforeEach
    void setUp() {
        codec = new CatalogCursorCodec(
                new ObjectMapper().findAndRegisterModules(),
                "catalog-cursor-test-secret-key-32bytes",
                Duration.ofMinutes(30),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 동일한_필터와_정렬의_커서를_복호화한다() {
        CatalogCursor cursor = validCursor();

        assertThat(codec.decode(codec.encode(cursor), "fingerprint")).isEqualTo(cursor);
    }

    @Test
    void 다른_필터의_커서는_거부한다() {
        assertThatThrownBy(() -> codec.decode(codec.encode(validCursor()), "other-filter"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CATALOG_CURSOR_INVALID);
    }

    @Test
    void 서명이_변조된_커서는_거부한다() {
        String token = codec.encode(validCursor());
        String tamperedToken = (token.startsWith("A") ? "B" : "A") + token.substring(1);

        assertThatThrownBy(() -> codec.decode(tamperedToken, "fingerprint"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CATALOG_CURSOR_INVALID);
    }

    @Test
    void 만료된_커서는_오래된_커서_오류를_반환한다() {
        CatalogCursor expiredCursor = new CatalogCursor(
                CatalogSort.NEWEST,
                null,
                42L,
                "fingerprint",
                NOW.minusSeconds(1)
        );

        assertThatThrownBy(() -> codec.decode(codec.encode(expiredCursor), "fingerprint"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CATALOG_CURSOR_STALE);
    }

    @Test
    void 커서_만료_시각은_구성된_최대_수명을_사용한다() {
        assertThat(codec.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    private CatalogCursor validCursor() {
        return new CatalogCursor(
                CatalogSort.PRICE_ASC,
                10_000L,
                42L,
                "fingerprint",
                NOW.plus(Duration.ofMinutes(30))
        );
    }
}
