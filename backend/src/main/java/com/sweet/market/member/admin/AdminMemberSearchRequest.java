package com.sweet.market.member.admin;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.domain.MemberRole;

public record AdminMemberSearchRequest(
        String email,
        String nickname,
        MemberRole role
) {

    public String normalizedEmail() {
        if (email == null || email.isBlank()) {
            return null;
        }
        return Member.normalizeEmail(email);
    }

    public String normalizedNickname() {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return nickname.trim();
    }
}
