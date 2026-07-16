package com.sweet.market.store.operations;

import com.sweet.market.store.domain.StoreMemberRole;

import java.time.LocalDateTime;

public record StoreMembershipResponse(
        Long membershipId,
        Long memberId,
        String memberNickname,
        StoreMemberRole role,
        LocalDateTime joinedAt
) {
}
