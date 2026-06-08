package com.sweet.market.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void createStoresNormalizedEmail() {
        Member member = Member.create("  USER@Example.COM  ", "encoded-password", "nickname");

        assertThat(member.getEmail()).isEqualTo("user@example.com");
    }
}
