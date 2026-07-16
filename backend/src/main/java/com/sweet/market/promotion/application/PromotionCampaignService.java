package com.sweet.market.promotion.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.api.PromotionCampaignCreateRequest;
import com.sweet.market.promotion.api.PromotionCampaignResponse;
import com.sweet.market.promotion.api.PromotionCampaignSearchRequest;
import com.sweet.market.promotion.api.PromotionCampaignUpdateRequest;
import com.sweet.market.promotion.domain.PromotionCampaign;
import com.sweet.market.promotion.domain.PromotionDomainError;
import com.sweet.market.promotion.domain.PromotionScope;
import com.sweet.market.promotion.repository.PromotionCampaignRepository;
import com.sweet.market.store.application.StoreAccessService;
import com.sweet.market.store.domain.Store;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PromotionCampaignService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant MIN_PERIOD = Instant.EPOCH;
    private static final Instant MAX_PERIOD = LocalDateTime.of(9999, 12, 31, 23, 59, 59).atZone(KST).toInstant();

    private final PromotionCampaignRepository promotionCampaignRepository;
    private final ProductRepository productRepository;
    private final StoreAccessService storeAccessService;
    private final Clock clock;

    @Autowired
    public PromotionCampaignService(
            PromotionCampaignRepository promotionCampaignRepository,
            ProductRepository productRepository,
            StoreAccessService storeAccessService
    ) {
        this(promotionCampaignRepository, productRepository, storeAccessService, Clock.systemUTC());
    }

    PromotionCampaignService(
            PromotionCampaignRepository promotionCampaignRepository,
            ProductRepository productRepository,
            StoreAccessService storeAccessService,
            Clock clock
    ) {
        this.promotionCampaignRepository = promotionCampaignRepository;
        this.productRepository = productRepository;
        this.storeAccessService = storeAccessService;
        this.clock = clock;
    }

    @Transactional
    public PromotionCampaignResponse create(Long memberId, Long storeId, PromotionCampaignCreateRequest request) {
        Store store = storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        List<Product> products = validatedTargets(storeId, request.scope(), request.productIds());
        try {
            PromotionCampaign campaign = PromotionCampaign.create(
                    store, request.scope(), request.discountType(), request.discountValue(), request.priority(),
                    request.title(), request.label(), toInstant(request.startsAt()), toInstant(request.endsAt()), products
            );
            return PromotionCampaignResponse.detail(promotionCampaignRepository.save(campaign), now());
        } catch (DomainException exception) {
            throw mapDomainException(exception);
        }
    }

    @Transactional(readOnly = true)
    public Page<PromotionCampaignResponse> findPage(
            Long memberId,
            Long storeId,
            PromotionCampaignSearchRequest request
    ) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        if (request.periodFrom() != null && request.periodTo() != null
                && request.periodFrom().isAfter(request.periodTo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Instant now = now();
        return promotionCampaignRepository.search(
                        storeId, request.status() != null, request.status() == null ? "" : request.status().name(),
                        request.periodFrom() == null ? MIN_PERIOD : toInstant(request.periodFrom()),
                        request.periodTo() == null ? MAX_PERIOD : toInstant(request.periodTo()), now,
                        PageRequest.of(request.resolvedPage(), request.resolvedSize())
                )
                .map(campaign -> PromotionCampaignResponse.summary(campaign, now));
    }

    @Transactional(readOnly = true)
    public PromotionCampaignResponse find(Long memberId, Long storeId, Long promotionId) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return PromotionCampaignResponse.detail(findCampaign(storeId, promotionId), now());
    }

    @Transactional
    public PromotionCampaignResponse update(
            Long memberId,
            Long storeId,
            Long promotionId,
            PromotionCampaignUpdateRequest request
    ) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        PromotionCampaign campaign = findCampaign(storeId, promotionId);
        List<Product> products = validatedTargets(storeId, request.scope(), request.productIds());
        try {
            campaign.update(
                    request.scope(), request.discountType(), request.discountValue(), request.priority(), request.title(), request.label(),
                    toInstant(request.startsAt()), toInstant(request.endsAt()), products, now()
            );
            return PromotionCampaignResponse.detail(campaign, now());
        } catch (DomainException exception) {
            throw mapDomainException(exception);
        }
    }

    @Transactional
    public PromotionCampaignResponse schedule(Long memberId, Long storeId, Long promotionId) {
        return transition(memberId, storeId, promotionId, campaign -> campaign.schedule(now()));
    }

    @Transactional
    public PromotionCampaignResponse pause(Long memberId, Long storeId, Long promotionId) {
        return transition(memberId, storeId, promotionId, campaign -> campaign.pause(now()));
    }

    @Transactional
    public PromotionCampaignResponse resume(Long memberId, Long storeId, Long promotionId) {
        return transition(memberId, storeId, promotionId, campaign -> campaign.resume(now()));
    }

    @Transactional
    public PromotionCampaignResponse end(Long memberId, Long storeId, Long promotionId) {
        return transition(memberId, storeId, promotionId, PromotionCampaign::end);
    }

    private PromotionCampaignResponse transition(Long memberId, Long storeId, Long promotionId, CampaignTransition transition) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        PromotionCampaign campaign = findCampaign(storeId, promotionId);
        try {
            transition.apply(campaign);
            return PromotionCampaignResponse.detail(campaign, now());
        } catch (DomainException exception) {
            throw mapDomainException(exception);
        }
    }

    private PromotionCampaign findCampaign(Long storeId, Long promotionId) {
        return promotionCampaignRepository.findByIdAndStoreId(promotionId, storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROMOTION_NOT_FOUND));
    }

    private List<Product> validatedTargets(Long storeId, PromotionScope scope, List<Long> productIds) {
        List<Long> safeProductIds = productIds == null ? List.of() : productIds;
        if (scope == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (scope == PromotionScope.SELECTED_PRODUCTS && safeProductIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (scope == PromotionScope.STORE_WIDE && !safeProductIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<Long> uniqueIds = new HashSet<>(safeProductIds);
        if (uniqueIds.size() != safeProductIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (scope == PromotionScope.STORE_WIDE) {
            return List.of();
        }
        List<Product> found = productRepository.findAllByStoreIdAndIdIn(storeId, new ArrayList<>(uniqueIds));
        if (found.size() != uniqueIds.size() || found.stream().anyMatch(product -> !product.isPurchasable())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return safeProductIds.stream()
                .map(id -> found.stream().filter(product -> product.getId().equals(id)).findFirst().orElseThrow())
                .toList();
    }

    private Instant toInstant(java.time.LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atZone(KST).toInstant();
    }

    private Instant now() {
        return clock.instant();
    }

    private BusinessException mapDomainException(DomainException exception) {
        PromotionDomainError error = (PromotionDomainError) exception.error();
        return switch (error) {
            case LIFECYCLE_TRANSITION_NOT_ALLOWED, UPDATE_NOT_ALLOWED ->
                    new BusinessException(ErrorCode.PROMOTION_LIFECYCLE_NOT_ALLOWED, exception);
            default -> new BusinessException(ErrorCode.VALIDATION_ERROR, exception);
        };
    }

    @FunctionalInterface
    private interface CampaignTransition {
        void apply(PromotionCampaign campaign);
    }
}
