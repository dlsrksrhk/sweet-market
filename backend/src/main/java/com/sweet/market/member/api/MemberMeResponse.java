package com.sweet.market.member.api;

import com.sweet.market.member.domain.Member;

public record MemberMeResponse(
        Long id,
        String email,
        String nickname
) {

    public static MemberMeResponse from(Member member) {
        return new MemberMeResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}
