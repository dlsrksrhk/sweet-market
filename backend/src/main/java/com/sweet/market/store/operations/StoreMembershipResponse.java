package com.sweet.market.store.operations;

import java.time.LocalDateTime;

import com.sweet.market.store.domain.StoreMemberRole;

public record StoreMembershipResponse(
        Long membershipId,
        Long memberId,
        String memberNickname,
        StoreMemberRole role,
        LocalDateTime joinedAt
) {
}
