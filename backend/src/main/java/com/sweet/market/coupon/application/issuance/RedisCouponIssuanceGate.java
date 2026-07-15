package com.sweet.market.coupon.application.issuance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisCouponIssuanceGate implements CouponIssuanceGate {

    private static final Duration CACHE_RECOVERY_GRACE = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> reserveScript;
    private final DefaultRedisScript<Long> completeScript;
    private final DefaultRedisScript<Long> releaseScript;

    public RedisCouponIssuanceGate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        reserveScript = script("redis/coupon-reserve.lua", List.class);
        completeScript = script("redis/coupon-complete.lua", Long.class);
        releaseScript = script("redis/coupon-release.lua", Long.class);
    }

    @Override
    public CouponIssuanceGateResult reserve(Long campaignId, Long memberId, int issueLimit,
            int issuedCount, Instant issueEndsAt, Instant now) {
        String token = UUID.randomUUID().toString();
        String keyPrefix = keyPrefix(campaignId);
        List<?> result = execute(reserveScript, List.of(
                keyPrefix + "count",
                keyPrefix + "pending",
                keyPrefix + "member:" + memberId),
                Long.toString(now.toEpochMilli()),
                Integer.toString(issuedCount),
                Integer.toString(issueLimit),
                token,
                Long.toString(now.plus(CouponIssuanceGate.RESERVATION_DURATION).toEpochMilli()),
                Long.toString(cacheTtlMillis(issueEndsAt, now)),
                memberId.toString(),
                keyPrefix + "member:");

        ReservationType type = ReservationType.valueOf(result.getFirst().toString());
        return type == ReservationType.RESERVED
                ? CouponIssuanceGateResult.reserved(new CouponIssuanceReservation(campaignId, memberId, token))
                : CouponIssuanceGateResult.of(type);
    }

    @Override
    public void complete(CouponIssuanceReservation reservation, Instant now) {
        String keyPrefix = keyPrefix(reservation.campaignId());
        execute(completeScript, List.of(
                keyPrefix + "count",
                keyPrefix + "pending",
                keyPrefix + "member:" + reservation.memberId()),
                pendingMemberToken(reservation),
                reservation.token(),
                Long.toString(now.toEpochMilli()));
    }

    @Override
    public void release(CouponIssuanceReservation reservation, Instant now) {
        String keyPrefix = keyPrefix(reservation.campaignId());
        execute(releaseScript, List.of(
                keyPrefix + "count",
                keyPrefix + "pending",
                keyPrefix + "member:" + reservation.memberId()),
                pendingMemberToken(reservation),
                reservation.token());
    }

    private <T> T execute(DefaultRedisScript<T> script, List<String> keys, String... arguments) {
        try {
            return redisTemplate.execute(script, keys, (Object[]) arguments);
        } catch (DataAccessException exception) {
            throw new CouponIssuanceGateUnavailableException(exception);
        }
    }

    private long cacheTtlMillis(Instant issueEndsAt, Instant now) {
        return Math.max(1L, Duration.between(now, issueEndsAt.plus(CACHE_RECOVERY_GRACE)).toMillis());
    }

    private static String keyPrefix(Long campaignId) {
        return "coupon:issue:{" + campaignId + "}:";
    }

    private static String pendingMemberToken(CouponIssuanceReservation reservation) {
        return reservation.memberId() + ":" + reservation.token();
    }

    private static <T> DefaultRedisScript<T> script(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(readScript(path));
        script.setResultType(resultType);
        return script;
    }

    private static String readScript(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Redis Lua 스크립트를 읽을 수 없습니다: " + path, exception);
        }
    }
}
