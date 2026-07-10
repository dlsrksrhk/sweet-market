package com.sweet.market.store.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;

class StoreMembershipTest {

    @Test
    void 소유자_멤버십은_활성_OWNER로_생성된다() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");
        Store store = Store.createPersonal(owner, "상점", "소개");

        StoreMembership membership = StoreMembership.createOwner(store, owner);

        assertThat(membership.getStore()).isSameAs(store);
        assertThat(membership.getMember()).isSameAs(owner);
        assertThat(membership.getRole()).isEqualTo(StoreMemberRole.OWNER);
        assertThat(membership.isActive()).isTrue();
    }

    @Test
    void 매니저_멤버십은_비활성화할_수_있다() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");
        Store store = Store.createPersonal(owner, "상점", "소개");
        StoreMembership membership = StoreMembership.createManager(store, Member.create("manager@example.com", "encoded-password", "manager"));

        membership.deactivate();

        assertThat(membership.getRole()).isEqualTo(StoreMemberRole.MANAGER);
        assertThat(membership.isActive()).isFalse();
    }
}
