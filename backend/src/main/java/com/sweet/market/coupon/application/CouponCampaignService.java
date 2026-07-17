package com.sweet.market.coupon.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
import com.sweet.market.operations.campaign.CampaignCommandEventFactory;
import com.sweet.market.operations.event.OperationalEventRecorder;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
public class CouponCampaignService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant MIN_PERIOD = Instant.EPOCH;
    private static final Instant MAX_PERIOD = LocalDateTime.of(9999, 12, 31, 23, 59, 59).atZone(KST).toInstant();
    private final CouponCampaignRepository campaignRepository;
    private final ProductRepository productRepository;
    private final StoreAccessService storeAccessService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final OperationalEventRecorder eventRecorder;
    private final CampaignCommandEventFactory eventFactory;

    @Autowired
    public CouponCampaignService(
            CouponCampaignRepository campaignRepository,
            ProductRepository productRepository,
            StoreAccessService storeAccessService,
            ApplicationEventPublisher eventPublisher,
            OperationalEventRecorder eventRecorder,
            CampaignCommandEventFactory eventFactory
    ) {
        this(campaignRepository, productRepository, storeAccessService, Clock.systemUTC(),
                eventPublisher, eventRecorder, eventFactory);
    }

    public CouponCampaignService(CouponCampaignRepository campaignRepository, ProductRepository productRepository, StoreAccessService storeAccessService) {
        this(campaignRepository, productRepository, storeAccessService, Clock.systemUTC(), event -> { });
    }

    CouponCampaignService(CouponCampaignRepository campaignRepository, ProductRepository productRepository, StoreAccessService storeAccessService, Clock clock) {
        this(campaignRepository, productRepository, storeAccessService, clock, event -> { });
    }

    CouponCampaignService(CouponCampaignRepository campaignRepository, ProductRepository productRepository, StoreAccessService storeAccessService, Clock clock, ApplicationEventPublisher eventPublisher) {
        this(campaignRepository, productRepository, storeAccessService, clock, eventPublisher,
                event -> { }, new CampaignCommandEventFactory(new ObjectMapper()));
    }

    CouponCampaignService(
            CouponCampaignRepository campaignRepository,
            ProductRepository productRepository,
            StoreAccessService storeAccessService,
            Clock clock,
            ApplicationEventPublisher eventPublisher,
            OperationalEventRecorder eventRecorder,
            CampaignCommandEventFactory eventFactory
    ) {
        this.campaignRepository = campaignRepository;
        this.productRepository = productRepository;
        this.storeAccessService = storeAccessService;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.eventRecorder = eventRecorder;
        this.eventFactory = eventFactory;
    }

    @Transactional
    public CouponCampaignResponse createStoreCampaign(Long memberId, Long storeId, CouponCampaignCreateRequest request) {
        Store store = storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return create(memberId, CouponCampaignOwnerType.STORE, store, request);
    }

    @Transactional
    public CouponCampaignResponse createPlatformCampaign(Long actorMemberId, CouponCampaignCreateRequest request) {
        return create(actorMemberId, CouponCampaignOwnerType.PLATFORM, null, request);
    }

    private CouponCampaignResponse create(
            Long actorMemberId,
            CouponCampaignOwnerType ownerType,
            Store store,
            CouponCampaignCreateRequest request
    ) {
        try {
            CouponCampaign campaign = CouponCampaign.create(ownerType, store, request.scope(), request.discountType(), request.discountValue(), request.maxDiscountAmount(), request.minimumPurchaseAmount(), request.stackable(), request.title(), request.label(), toInstant(request.issueStartsAt()), toInstant(request.issueEndsAt()), request.validityType(), toInstant(request.commonExpiresAt()), request.validityDays(), request.issueLimit(), validatedTargets(ownerType, store, request));
            campaignRepository.saveAndFlush(campaign);
            CouponCampaignResponse response = CouponCampaignResponse.detail(campaign, now());
            record(campaign, actorMemberId, "CREATED", null);
            invalidateDiscovery();
            return response;
        } catch (DomainException exception) {
            throw map(exception);
        }
    }

    @Transactional(readOnly = true)
    public Page<CouponCampaignResponse> findStorePage(Long memberId, Long storeId, CouponCampaignSearchRequest request) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        validatePeriod(request);
        return search(CouponCampaignOwnerType.STORE, storeId, request);
    }

    @Transactional(readOnly = true)
    public Page<CouponCampaignResponse> findPlatformPage(CouponCampaignSearchRequest request) {
        validatePeriod(request);
        return search(CouponCampaignOwnerType.PLATFORM, null, request);
    }

    @Transactional(readOnly = true)
    public CouponCampaignResponse findStore(Long memberId, Long storeId, Long campaignId) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return CouponCampaignResponse.detail(storeCampaign(storeId, campaignId), now());
    }

    @Transactional(readOnly = true)
    public CouponCampaignResponse findPlatform(Long campaignId) {
        return CouponCampaignResponse.detail(platformCampaign(campaignId), now());
    }

    @Transactional
    public CouponCampaignResponse updateStore(Long memberId, Long storeId, Long campaignId, CouponCampaignUpdateRequest request) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return update(memberId, storeCampaign(storeId, campaignId), request);
    }

    @Transactional
    public CouponCampaignResponse updatePlatform(Long actorMemberId, Long campaignId, CouponCampaignUpdateRequest request) {
        return update(actorMemberId, platformCampaign(campaignId), request);
    }

    private CouponCampaignResponse update(Long actorMemberId, CouponCampaign campaign, CouponCampaignUpdateRequest request) {
        JsonNode beforeSummary = eventFactory.summary(campaign);
        try {
            CouponCampaignCreateRequest input = request.asCreateRequest();
            campaign.update(input.scope(), input.discountType(), input.discountValue(), input.maxDiscountAmount(), input.minimumPurchaseAmount(), input.stackable(), input.title(), input.label(), toInstant(input.issueStartsAt()), toInstant(input.issueEndsAt()), input.validityType(), toInstant(input.commonExpiresAt()), input.validityDays(), input.issueLimit(), validatedTargets(campaign.getOwnerType(), campaign.getStore(), input), now());
            campaignRepository.flush();
            CouponCampaignResponse response = CouponCampaignResponse.detail(campaign, now());
            record(campaign, actorMemberId, "UPDATED", beforeSummary);
            invalidateDiscovery();
            return response;
        } catch (DomainException exception) {
            throw map(exception);
        }
    }

    @Transactional
    public CouponCampaignResponse scheduleStore(Long memberId, Long storeId, Long id) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return transition(memberId, storeCampaign(storeId, id), "SCHEDULED", c -> c.schedule(now()));
    }

    @Transactional
    public CouponCampaignResponse pauseStore(Long memberId, Long storeId, Long id) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return transition(memberId, storeCampaign(storeId, id), "PAUSED", c -> c.pause(now()));
    }

    @Transactional
    public CouponCampaignResponse resumeStore(Long memberId, Long storeId, Long id) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return transition(memberId, storeCampaign(storeId, id), "RESUMED", c -> c.resume(now()));
    }

    @Transactional
    public CouponCampaignResponse endStore(Long memberId, Long storeId, Long id) {
        storeAccessService.requireActiveBusinessOwner(memberId, storeId);
        return transition(memberId, storeCampaign(storeId, id), "ENDED", CouponCampaign::end);
    }

    @Transactional
    public CouponCampaignResponse schedulePlatform(Long actorMemberId, Long id) {
        return transition(actorMemberId, platformCampaign(id), "SCHEDULED", c -> c.schedule(now()));
    }

    @Transactional
    public CouponCampaignResponse pausePlatform(Long actorMemberId, Long id) {
        return transition(actorMemberId, platformCampaign(id), "PAUSED", c -> c.pause(now()));
    }

    @Transactional
    public CouponCampaignResponse resumePlatform(Long actorMemberId, Long id) {
        return transition(actorMemberId, platformCampaign(id), "RESUMED", c -> c.resume(now()));
    }

    @Transactional
    public CouponCampaignResponse endPlatform(Long actorMemberId, Long id) {
        return transition(actorMemberId, platformCampaign(id), "ENDED", CouponCampaign::end);
    }

    private CouponCampaignResponse transition(
            Long actorMemberId,
            CouponCampaign campaign,
            String command,
            Transition transition
    ) {
        JsonNode beforeSummary = eventFactory.summary(campaign);
        try {
            transition.apply(campaign);
            campaignRepository.flush();
            CouponCampaignResponse response = CouponCampaignResponse.detail(campaign, now());
            record(campaign, actorMemberId, command, beforeSummary);
            invalidateDiscovery();
            return response;
        } catch (DomainException exception) {
            throw map(exception);
        }
    }

    private CouponCampaign storeCampaign(Long storeId, Long id) {
        return campaignRepository.findWithDetailsByIdAndStoreId(id, storeId).orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND));
    }

    private CouponCampaign platformCampaign(Long id) {
        return campaignRepository.findWithDetailsByIdAndOwnerType(id, CouponCampaignOwnerType.PLATFORM).orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND));
    }

    private List<Product> validatedTargets(CouponCampaignOwnerType ownerType, Store store, CouponCampaignCreateRequest request) {
        List<Long> ids = request.productIds() == null ? List.of() : request.productIds();
        if (request.scope() == null || (request.scope() == CouponScope.SELECTED_PRODUCTS && ids.isEmpty()) || (request.scope() == CouponScope.ALL_PRODUCTS && !ids.isEmpty()))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (request.scope() == CouponScope.ALL_PRODUCTS) return List.of();
        List<Product> found = ownerType == CouponCampaignOwnerType.STORE ? productRepository.findAllByStoreIdAndIdIn(store.getId(), new ArrayList<>(unique)) : productRepository.findAllById(new ArrayList<>(unique));
        if (found.size() != unique.size() || found.stream().anyMatch(product -> !product.isPurchasable()))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return ids.stream().map(id -> found.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow()).toList();
    }

    private void validatePeriod(CouponCampaignSearchRequest request) {
        if (request.periodFrom() != null && request.periodTo() != null && request.periodFrom().isAfter(request.periodTo()))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private Page<CouponCampaignResponse> search(CouponCampaignOwnerType ownerType, Long storeId, CouponCampaignSearchRequest request) {
        Instant now = now();
        return campaignRepository.search(ownerType, storeId, request.status() != null, request.status() == null ? "" : request.status().name(),
                        request.periodFrom() == null ? MIN_PERIOD : toInstant(request.periodFrom()), request.periodTo() == null ? MAX_PERIOD : toInstant(request.periodTo()), now, page(request))
                .map(campaign -> CouponCampaignResponse.summary(campaign, now));
    }

    private PageRequest page(CouponCampaignSearchRequest request) {
        return PageRequest.of(request.resolvedPage(), request.resolvedSize());
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toInstant();
    }

    private Instant now() {
        return clock.instant();
    }

    private BusinessException map(DomainException exception) {
        CouponDomainError error = (CouponDomainError) exception.error();
        return switch (error) {
            case LIFECYCLE_TRANSITION_NOT_ALLOWED, UPDATE_NOT_ALLOWED ->
                    new BusinessException(ErrorCode.COUPON_LIFECYCLE_NOT_ALLOWED, exception);
            case ISSUE_LIMIT_EXCEEDED -> new BusinessException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED, exception);
            default -> new BusinessException(ErrorCode.VALIDATION_ERROR, exception);
        };
    }

    private void invalidateDiscovery() {
        eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
    }

    private void record(
            CouponCampaign campaign,
            Long actorMemberId,
            String command,
            JsonNode beforeSummary
    ) {
        Long ownerStoreId = campaign.getStore() == null ? null : campaign.getStore().getId();
        eventRecorder.record(eventFactory.completed(
                "COUPON", campaign.getId(), campaign.getVersion(), campaign.getOwnerType().name(), ownerStoreId,
                actorMemberId, command, beforeSummary, eventFactory.summary(campaign), now()
        ));
    }

    @FunctionalInterface
    private interface Transition {
        void apply(CouponCampaign campaign);
    }
}
