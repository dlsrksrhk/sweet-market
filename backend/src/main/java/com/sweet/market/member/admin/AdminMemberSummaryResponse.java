package com.sweet.market.member.admin;

import com.sweet.market.member.domain.MemberRole;

public record AdminMemberSummaryResponse(
        Long memberId,
        String email,
        String nickname,
        String role
) {

    public AdminMemberSummaryResponse(
            Long memberId,
            String email,
            String nickname,
            MemberRole role
    ) {
        this(memberId, email, nickname, role.name());
    }
}
