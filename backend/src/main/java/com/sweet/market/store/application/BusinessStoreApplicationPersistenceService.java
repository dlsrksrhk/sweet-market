package com.sweet.market.store.application;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.store.api.BusinessStoreApplicationRequest;
import com.sweet.market.store.api.StorePrivateResponse;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessStoreApplicationPersistenceService {

    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final StoreMembershipRepository storeMembershipRepository;

    public BusinessStoreApplicationPersistenceService(
            MemberRepository memberRepository,
            StoreRepository storeRepository,
            StoreMembershipRepository storeMembershipRepository
    ) {
        this.memberRepository = memberRepository;
        this.storeRepository = storeRepository;
        this.storeMembershipRepository = storeMembershipRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StorePrivateResponse persist(Long memberId, BusinessStoreApplicationRequest request) {
        Member owner = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Store store = storeRepository.saveAndFlush(Store.applyBusiness(
                owner, request.publicName(), request.introduction(), request.legalBusinessName(), request.businessRegistrationId()
        ));
        storeMembershipRepository.saveAndFlush(StoreMembership.createOwner(store, owner));
        return StorePrivateResponse.from(store);
    }
}
