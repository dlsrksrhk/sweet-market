package com.sweet.market.store.application;

import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
import com.sweet.market.member.domain.Member;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProvisioningService {

    private final StoreRepository storeRepository;
    private final StoreMembershipRepository storeMembershipRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StoreProvisioningService(
            StoreRepository storeRepository,
            StoreMembershipRepository storeMembershipRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.storeRepository = storeRepository;
        this.storeMembershipRepository = storeMembershipRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Store provisionPersonalStore(Member member) {
        Store store = storeRepository.findPersonalByOwnerMemberId(member.getId())
                .orElseGet(() -> storeRepository.save(Store.createPersonal(member, member.getNickname() + "의 상점", "")));
        if (store.getStatus() == StoreStatus.SUSPENDED) {
            store.reactivate();
            eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
        }
        StoreMembership membership = storeMembershipRepository.findByStoreIdAndMemberId(store.getId(), member.getId())
                .orElseGet(() -> storeMembershipRepository.save(StoreMembership.createOwner(store, member)));
        if (!membership.isActive()) {
            membership.activate();
        }
        return store;
    }
}
