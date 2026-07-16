package com.sweet.market.store.operations;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.store.application.StoreAccessService;
import com.sweet.market.store.domain.StoreMemberRole;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreMembershipCommandService {

    private final StoreAccessService storeAccessService;
    private final StoreMembershipRepository storeMembershipRepository;

    public StoreMembershipCommandService(
            StoreAccessService storeAccessService,
            StoreMembershipRepository storeMembershipRepository
    ) {
        this.storeAccessService = storeAccessService;
        this.storeMembershipRepository = storeMembershipRepository;
    }

    @Transactional
    public void removeManager(Long memberId, Long storeId, Long membershipId) {
        storeAccessService.requireOwner(memberId, storeId);
        StoreMembership target = storeMembershipRepository.findByIdAndStoreIdAndActiveTrue(membershipId, storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_ACCESS_DENIED));
        if (target.getRole() == StoreMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.STORE_OWNER_MEMBERSHIP_PROTECTED);
        }
        target.deactivate();
    }
}
