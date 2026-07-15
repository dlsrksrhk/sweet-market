package com.sweet.market.coupon.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.api.CouponCampaignCreateRequest;
import com.sweet.market.coupon.api.CouponCampaignResponse;
import com.sweet.market.coupon.api.CouponCampaignSearchRequest;
import com.sweet.market.coupon.api.CouponCampaignUpdateRequest;
import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.domain.CouponDomainError;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.repository.CouponCampaignRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.application.StoreAccessService;
import com.sweet.market.store.domain.Store;

@Service
public class CouponCampaignService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant MIN_PERIOD = Instant.EPOCH;
    private static final Instant MAX_PERIOD = LocalDateTime.of(9999, 12, 31, 23, 59, 59).atZone(KST).toInstant();
    private final CouponCampaignRepository campaignRepository;
    private final ProductRepository productRepository;
    private final StoreAccessService storeAccessService;
    private final Clock clock;

    @Autowired
    public CouponCampaignService(CouponCampaignRepository campaignRepository, ProductRepository productRepository, StoreAccessService storeAccessService) {
        this(campaignRepository, productRepository, storeAccessService, Clock.systemUTC());
    }
    CouponCampaignService(CouponCampaignRepository campaignRepository, ProductRepository productRepository, StoreAccessService storeAccessService, Clock clock) {
        this.campaignRepository = campaignRepository; this.productRepository = productRepository; this.storeAccessService = storeAccessService; this.clock = clock;
    }
    @Transactional
    public CouponCampaignResponse createStoreCampaign(Long memberId, Long storeId, CouponCampaignCreateRequest request) {
        Store store = storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return create(CouponCampaignOwnerType.STORE, store, request);
    }
    @Transactional
    public CouponCampaignResponse createPlatformCampaign(CouponCampaignCreateRequest request) { return create(CouponCampaignOwnerType.PLATFORM, null, request); }
    private CouponCampaignResponse create(CouponCampaignOwnerType ownerType, Store store, CouponCampaignCreateRequest request) {
        try {
            CouponCampaign campaign = CouponCampaign.create(ownerType, store, request.scope(), request.discountType(), request.discountValue(), request.maxDiscountAmount(), request.minimumPurchaseAmount(), request.stackable(), request.title(), request.label(), toInstant(request.issueStartsAt()), toInstant(request.issueEndsAt()), request.validityType(), toInstant(request.commonExpiresAt()), request.validityDays(), request.issueLimit(), validatedTargets(ownerType, store, request));
            return CouponCampaignResponse.detail(campaignRepository.save(campaign), now());
        } catch (DomainException exception) { throw map(exception); }
    }
    @Transactional(readOnly = true)
    public Page<CouponCampaignResponse> findStorePage(Long memberId, Long storeId, CouponCampaignSearchRequest request) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId); validatePeriod(request);
        return search(CouponCampaignOwnerType.STORE, storeId, request);
    }
    @Transactional(readOnly = true)
    public Page<CouponCampaignResponse> findPlatformPage(CouponCampaignSearchRequest request) {
        validatePeriod(request); return search(CouponCampaignOwnerType.PLATFORM, null, request);
    }
    @Transactional(readOnly = true)
    public CouponCampaignResponse findStore(Long memberId, Long storeId, Long campaignId) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId); return CouponCampaignResponse.detail(storeCampaign(storeId, campaignId), now());
    }
    @Transactional(readOnly = true)
    public CouponCampaignResponse findPlatform(Long campaignId) { return CouponCampaignResponse.detail(platformCampaign(campaignId), now()); }
    @Transactional
    public CouponCampaignResponse updateStore(Long memberId, Long storeId, Long campaignId, CouponCampaignUpdateRequest request) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId); return update(storeCampaign(storeId, campaignId), request);
    }
    @Transactional
    public CouponCampaignResponse updatePlatform(Long campaignId, CouponCampaignUpdateRequest request) { return update(platformCampaign(campaignId), request); }
    private CouponCampaignResponse update(CouponCampaign campaign, CouponCampaignUpdateRequest request) {
        try { CouponCampaignCreateRequest input = request.asCreateRequest(); campaign.update(input.scope(), input.discountType(), input.discountValue(), input.maxDiscountAmount(), input.minimumPurchaseAmount(), input.stackable(), input.title(), input.label(), toInstant(input.issueStartsAt()), toInstant(input.issueEndsAt()), input.validityType(), toInstant(input.commonExpiresAt()), input.validityDays(), input.issueLimit(), validatedTargets(campaign.getOwnerType(), campaign.getStore(), input), now()); return CouponCampaignResponse.detail(campaign, now()); }
        catch (DomainException exception) { throw map(exception); }
    }
    @Transactional public CouponCampaignResponse scheduleStore(Long memberId, Long storeId, Long id) { storeAccessService.requireActiveBusinessOwner(memberId, storeId); return transition(storeCampaign(storeId, id), c -> c.schedule(now())); }
    @Transactional public CouponCampaignResponse pauseStore(Long memberId, Long storeId, Long id) { storeAccessService.requireActiveBusinessOwner(memberId, storeId); return transition(storeCampaign(storeId, id), c -> c.pause(now())); }
    @Transactional public CouponCampaignResponse resumeStore(Long memberId, Long storeId, Long id) { storeAccessService.requireActiveBusinessOwner(memberId, storeId); return transition(storeCampaign(storeId, id), c -> c.resume(now())); }
    @Transactional public CouponCampaignResponse endStore(Long memberId, Long storeId, Long id) { storeAccessService.requireActiveBusinessOwner(memberId, storeId); return transition(storeCampaign(storeId, id), CouponCampaign::end); }
    @Transactional public CouponCampaignResponse schedulePlatform(Long id) { return transition(platformCampaign(id), c -> c.schedule(now())); }
    @Transactional public CouponCampaignResponse pausePlatform(Long id) { return transition(platformCampaign(id), c -> c.pause(now())); }
    @Transactional public CouponCampaignResponse resumePlatform(Long id) { return transition(platformCampaign(id), c -> c.resume(now())); }
    @Transactional public CouponCampaignResponse endPlatform(Long id) { return transition(platformCampaign(id), CouponCampaign::end); }
    private CouponCampaignResponse transition(CouponCampaign campaign, Transition transition) { try { transition.apply(campaign); return CouponCampaignResponse.detail(campaign, now()); } catch (DomainException exception) { throw map(exception); } }
    private CouponCampaign storeCampaign(Long storeId, Long id) { return campaignRepository.findWithDetailsByIdAndStoreId(id, storeId).orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND)); }
    private CouponCampaign platformCampaign(Long id) { return campaignRepository.findWithDetailsByIdAndOwnerType(id, CouponCampaignOwnerType.PLATFORM).orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND)); }
    private List<Product> validatedTargets(CouponCampaignOwnerType ownerType, Store store, CouponCampaignCreateRequest request) {
        List<Long> ids = request.productIds() == null ? List.of() : request.productIds();
        if (request.scope() == null || (request.scope() == CouponScope.SELECTED_PRODUCTS && ids.isEmpty()) || (request.scope() == CouponScope.ALL_PRODUCTS && !ids.isEmpty())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        Set<Long> unique = new HashSet<>(ids); if (unique.size() != ids.size()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (request.scope() == CouponScope.ALL_PRODUCTS) return List.of();
        List<Product> found = ownerType == CouponCampaignOwnerType.STORE ? productRepository.findAllByStoreIdAndIdIn(store.getId(), new ArrayList<>(unique)) : productRepository.findAllById(new ArrayList<>(unique));
        if (found.size() != unique.size() || found.stream().anyMatch(product -> !product.isPurchasable())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return ids.stream().map(id -> found.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow()).toList();
    }
    private void validatePeriod(CouponCampaignSearchRequest request) { if (request.periodFrom() != null && request.periodTo() != null && request.periodFrom().isAfter(request.periodTo())) throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
    private Page<CouponCampaignResponse> search(CouponCampaignOwnerType ownerType, Long storeId, CouponCampaignSearchRequest request) {
        Instant now = now();
        return campaignRepository.search(ownerType, storeId, request.status() != null, request.status() == null ? "" : request.status().name(),
                request.periodFrom() == null ? MIN_PERIOD : toInstant(request.periodFrom()), request.periodTo() == null ? MAX_PERIOD : toInstant(request.periodTo()), now, page(request))
                .map(campaign -> CouponCampaignResponse.summary(campaign, now));
    }
    private PageRequest page(CouponCampaignSearchRequest request) { return PageRequest.of(request.resolvedPage(), request.resolvedSize()); }
    private Instant toInstant(LocalDateTime value) { return value == null ? null : value.atZone(KST).toInstant(); }
    private Instant now() { return clock.instant(); }
    private BusinessException map(DomainException exception) { CouponDomainError error = (CouponDomainError) exception.error(); return switch (error) { case LIFECYCLE_TRANSITION_NOT_ALLOWED, UPDATE_NOT_ALLOWED -> new BusinessException(ErrorCode.COUPON_LIFECYCLE_NOT_ALLOWED, exception); case ISSUE_LIMIT_EXCEEDED -> new BusinessException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED, exception); default -> new BusinessException(ErrorCode.VALIDATION_ERROR, exception); }; }
    @FunctionalInterface private interface Transition { void apply(CouponCampaign campaign); }
}
