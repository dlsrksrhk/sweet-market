package com.sweet.market.store.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.store.api.BusinessStoreApplicationRequest;
import com.sweet.market.store.api.StorePrivateResponse;
import com.sweet.market.store.api.StoreProfileUpdateRequest;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.domain.StoreType;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;

@Service
public class StoreGovernanceService {

    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final StoreMembershipRepository storeMembershipRepository;
    private final StoreAccessService storeAccessService;

    public StoreGovernanceService(
            MemberRepository memberRepository,
            StoreRepository storeRepository,
            StoreMembershipRepository storeMembershipRepository,
            StoreAccessService storeAccessService
    ) {
        this.memberRepository = memberRepository;
        this.storeRepository = storeRepository;
        this.storeMembershipRepository = storeMembershipRepository;
        this.storeAccessService = storeAccessService;
    }

    @Transactional
    public StorePrivateResponse applyBusiness(Long memberId, BusinessStoreApplicationRequest request) {
        if (!storeRepository.findBusinessByOwnerMemberId(memberId).isEmpty()) {
            throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_STORE);
        }
        Member owner = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Store store = storeRepository.save(Store.applyBusiness(
                owner, request.publicName(), request.introduction(), request.legalBusinessName(), request.businessRegistrationId()
        ));
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        return StorePrivateResponse.from(store);
    }

    @Transactional
    public StorePrivateResponse resubmitBusiness(Long memberId, Long storeId, BusinessStoreApplicationRequest request) {
        Store store = storeAccessService.requireOwner(memberId, storeId);
        validateBusiness(store);
        if (store.getStatus() != StoreStatus.REJECTED) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED);
        }
        store.changePublicInformation(request.publicName(), request.introduction());
        store.changeLegalBusinessInformationForResubmission(request.legalBusinessName(), request.businessRegistrationId());
        store.resubmit();
        return StorePrivateResponse.from(store);
    }

    @Transactional
    public StorePrivateResponse updateProfile(Long memberId, Long storeId, StoreProfileUpdateRequest request) {
        Store store = storeAccessService.requireOwner(memberId, storeId);
        store.changePublicInformation(request.publicName(), request.introduction());
        if (request.includesLegalBusinessInformation()) {
            if (request.legalBusinessName() == null || request.legalBusinessName().isBlank()
                    || request.businessRegistrationId() == null || request.businessRegistrationId().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            try {
                store.changeLegalBusinessInformation(request.legalBusinessName(), request.businessRegistrationId());
            } catch (IllegalStateException exception) {
                throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED);
            }
        }
        return StorePrivateResponse.from(store);
    }

    @Transactional
    public StorePrivateResponse approve(Long storeId) {
        Store store = requireBusiness(storeId);
        try {
            store.approve();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED);
        }
        return StorePrivateResponse.from(store);
    }

    @Transactional
    public StorePrivateResponse reject(Long storeId, String reason) {
        Store store = requireBusiness(storeId);
        try {
            store.reject(reason);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED);
        }
        return StorePrivateResponse.from(store);
    }

    @Transactional
    public StorePrivateResponse suspend(Long storeId) {
        Store store = requireBusiness(storeId);
        try {
            store.suspend();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED);
        }
        return StorePrivateResponse.from(store);
    }

    @Transactional
    public StorePrivateResponse reactivate(Long storeId) {
        Store store = requireBusiness(storeId);
        try {
            store.reactivate();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED);
        }
        return StorePrivateResponse.from(store);
    }

    private Store requireBusiness(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        validateBusiness(store);
        return store;
    }

    private void validateBusiness(Store store) {
        if (store.getType() != StoreType.BUSINESS) {
            throw new BusinessException(ErrorCode.STORE_INVALID_TYPE);
        }
    }
}
