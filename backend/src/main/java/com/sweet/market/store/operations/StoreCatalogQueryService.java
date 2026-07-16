package com.sweet.market.store.operations;

import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.application.StoreAccessService;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.repository.StoreMembershipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StoreCatalogQueryService {

    private final StoreAccessService storeAccessService;
    private final StoreMembershipRepository storeMembershipRepository;
    private final ProductRepository productRepository;

    public StoreCatalogQueryService(
            StoreAccessService storeAccessService,
            StoreMembershipRepository storeMembershipRepository,
            ProductRepository productRepository
    ) {
        this.storeAccessService = storeAccessService;
        this.storeMembershipRepository = storeMembershipRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<OperableStoreResponse> findOperableStores(Long memberId) {
        return storeMembershipRepository.findOperableStores(memberId);
    }

    @Transactional(readOnly = true)
    public StoreCatalogSummaryResponse findSummary(Long memberId, Long storeId) {
        Store store = storeAccessService.requireOperator(memberId, storeId);
        return productRepository.summarizeStoreCatalog(storeId)
                .withCatalogWritable(store.getStatus() == StoreStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Page<StoreCatalogProductResponse> findProducts(
            Long memberId,
            Long storeId,
            StoreCatalogSearchRequest request
    ) {
        storeAccessService.requireOperator(memberId, storeId);
        Sort sort = request.resolvedSort() == StoreCatalogSort.NEWEST
                ? Sort.by(Sort.Order.desc("id"))
                : Sort.by(Sort.Order.asc("id"));
        PageRequest pageRequest = PageRequest.of(request.resolvedPage(), request.resolvedSize(), sort);
        return productRepository.searchStoreCatalog(
                storeId,
                request.status(),
                request.normalizedKeyword(),
                pageRequest
        );
    }
}
