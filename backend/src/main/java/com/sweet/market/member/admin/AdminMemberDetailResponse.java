package com.sweet.market.member.admin;

import com.sweet.market.member.domain.Member;

public record AdminMemberDetailResponse(
        Long memberId,
        String email,
        String nickname,
        String role,
        long productCount,
        long orderCount
) {

    public static AdminMemberDetailResponse from(Member member, long productCount, long orderCount) {
        return new AdminMemberDetailResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole().name(),
                productCount,
                orderCount
        );
    }
}
