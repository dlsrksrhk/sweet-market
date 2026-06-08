package com.sweet.market.auth.api;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        MemberResponse member
) {

    public static AuthResponse bearer(String accessToken, long expiresIn, MemberResponse member) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, member);
    }
}
