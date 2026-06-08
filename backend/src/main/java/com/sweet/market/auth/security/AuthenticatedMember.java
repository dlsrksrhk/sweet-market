package com.sweet.market.auth.security;

public record AuthenticatedMember(
        Long id,
        String email
) {
}
