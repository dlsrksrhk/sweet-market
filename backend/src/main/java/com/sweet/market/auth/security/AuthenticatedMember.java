package com.sweet.market.auth.security;

import com.sweet.market.member.domain.MemberRole;

public record AuthenticatedMember(
        Long id,
        String email,
        MemberRole role
) {
}
