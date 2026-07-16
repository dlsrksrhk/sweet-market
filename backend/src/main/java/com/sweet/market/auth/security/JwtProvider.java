package com.sweet.market.auth.security;

import com.sweet.market.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenValiditySeconds;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = properties.accessTokenValiditySeconds();
    }

    public String createAccessToken(Long memberId, String email, MemberRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenValiditySeconds);

        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public AuthenticatedMember parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long memberId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            MemberRole role = parseRole(claims);
            return new AuthenticatedMember(memberId, email, role);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidJwtException();
        }
    }

    private MemberRole parseRole(Claims claims) {
        String role = claims.get("role", String.class);
        if (role == null) {
            throw new InvalidJwtException();
        }

        try {
            return MemberRole.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtException();
        }
    }

    public long accessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    public static class InvalidJwtException extends RuntimeException {
    }
}
