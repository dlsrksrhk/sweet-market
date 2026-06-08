package com.sweet.market.auth.api;

import com.sweet.market.member.domain.Member;

public record MemberResponse(
        Long id,
        String email,
        String nickname
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}
