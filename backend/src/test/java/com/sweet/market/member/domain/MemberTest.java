package com.sweet.market.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void 회원_생성은_정규화된_이메일을_저장한다() {
        Member member = Member.create("  USER@Example.COM  ", "encoded-password", "nickname");

        assertThat(member.getEmail()).isEqualTo("user@example.com");
    }
}
