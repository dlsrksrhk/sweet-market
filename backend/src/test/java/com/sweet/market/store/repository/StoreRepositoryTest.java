package com.sweet.market.store.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.support.IntegrationTestSupport;

class StoreRepositoryTest extends IntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private StoreRepository storeRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private MemberRepository memberRepository;

    @Test
    void 오래된_상점_상태_변경은_낙관적_락_예외가_발생한다() {
        Member owner = memberRepository.save(Member.create("owner@example.com", "encoded-password", "owner"));
        Store store = storeRepository.saveAndFlush(Store.applyBusiness(owner, "상점", "소개", "상호", "123-45-67890"));

        Store approvedStore = storeRepository.findById(store.getId()).orElseThrow();
        Store staleStore = storeRepository.findById(store.getId()).orElseThrow();
        approvedStore.approve();
        storeRepository.saveAndFlush(approvedStore);
        staleStore.reject("오래된 반려");

        assertThatThrownBy(() -> storeRepository.saveAndFlush(staleStore))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
