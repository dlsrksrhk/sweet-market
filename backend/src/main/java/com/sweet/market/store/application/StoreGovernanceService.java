package com.sweet.market.store.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
import com.sweet.market.store.api.BusinessStoreApplicationRequest;
import com.sweet.market.store.api.StorePrivateResponse;
import com.sweet.market.store.api.StoreProfileUpdateRequest;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreDomainError;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.domain.StoreType;
import com.sweet.market.store.repository.StoreRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreGovernanceService {

    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final BusinessStoreApplicationPersistenceService businessStoreApplicationPersistenceService;
    private final ApplicationEventPublisher eventPublisher;

    public StoreGovernanceService(
            StoreRepository storeRepository,
            StoreAccessService storeAccessService,
            BusinessStoreApplicationPersistenceService businessStoreApplicationPersistenceService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.storeRepository = storeRepository;
        this.storeAccessService = storeAccessService;
        this.businessStoreApplicationPersistenceService = businessStoreApplicationPersistenceService;
        this.eventPublisher = eventPublisher;
    }

    public StorePrivateResponse applyBusiness(Long memberId, BusinessStoreApplicationRequest request) {
        if (!storeRepository.findBusinessByOwnerMemberId(memberId).isEmpty()) {
            throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_STORE);
        }
        try {
            return businessStoreApplicationPersistenceService.persist(memberId, request);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_STORE);
        }
    }

    @Transactional
    public StorePrivateResponse resubmitBusiness(Long memberId, Long storeId, BusinessStoreApplicationRequest request) {
        Store store = storeAccessService.requireOwner(memberId, storeId);
        validateBusiness(store);
        if (store.getStatus() != StoreStatus.REJECTED) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED);
        }
        try {
            store.changePublicInformation(request.publicName(), request.introduction());
            store.changeLegalBusinessInformationForResubmission(request.legalBusinessName(), request.businessRegistrationId());
            store.resubmit();
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED, exception);
        }
        StorePrivateResponse response = StorePrivateResponse.from(store);
        invalidateDiscovery();
        return response;
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
            } catch (DomainException exception) {
                throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED, exception);
            }
        }
        StorePrivateResponse response = StorePrivateResponse.from(store);
        invalidateDiscovery();
        return response;
    }

    @Transactional
    public StorePrivateResponse approve(Long storeId) {
        Store store = requireBusiness(storeId);
        try {
            store.approve();
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED, exception);
        }
        StorePrivateResponse response = StorePrivateResponse.from(store);
        invalidateDiscovery();
        return response;
    }

    @Transactional
    public StorePrivateResponse reject(Long storeId, String reason) {
        Store store = requireBusiness(storeId);
        try {
            store.reject(reason);
        } catch (DomainException exception) {
            if (exception.error() == StoreDomainError.REJECTION_REASON_REQUIRED) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception);
            }
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED, exception);
        }
        StorePrivateResponse response = StorePrivateResponse.from(store);
        invalidateDiscovery();
        return response;
    }

    @Transactional
    public StorePrivateResponse suspend(Long storeId) {
        Store store = requireBusiness(storeId);
        try {
            store.suspend();
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED, exception);
        }
        StorePrivateResponse response = StorePrivateResponse.from(store);
        invalidateDiscovery();
        return response;
    }

    @Transactional
    public StorePrivateResponse reactivate(Long storeId) {
        Store store = requireBusiness(storeId);
        try {
            store.reactivate();
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.STORE_CHANGE_NOT_ALLOWED, exception);
        }
        StorePrivateResponse response = StorePrivateResponse.from(store);
        invalidateDiscovery();
        return response;
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

    private void invalidateDiscovery() {
        eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
    }
}
