package com.sweet.market.store;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

class StoreOperationsApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMembershipRepository storeMembershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 소유자는_활성_멤버십을_소유자와_매니저_순으로_조회한다() throws Exception {
        Member owner = saveMember("membership-list-owner@example.com", "소유자");
        Member firstManager = saveMember("membership-list-first@example.com", "첫 매니저");
        Member secondManager = saveMember("membership-list-second@example.com", "둘째 매니저");
        Member inactiveManager = saveMember("membership-list-inactive@example.com", "비활성 매니저");
        Store store = saveActiveBusinessStore(owner, "멤버십 상점");
        StoreMembership first = storeMembershipRepository.save(StoreMembership.createManager(store, firstManager));
        StoreMembership second = storeMembershipRepository.save(StoreMembership.createManager(store, secondManager));
        StoreMembership inactive = StoreMembership.createManager(store, inactiveManager);
        inactive.deactivate();
        storeMembershipRepository.save(inactive);

        mockMvc.perform(get("/api/store-operations/{storeId}/memberships", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].memberId").value(owner.getId()))
                .andExpect(jsonPath("$.data[0].memberNickname").value("소유자"))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[1].membershipId").value(first.getId()))
                .andExpect(jsonPath("$.data[1].memberId").value(firstManager.getId()))
                .andExpect(jsonPath("$.data[1].memberNickname").value("첫 매니저"))
                .andExpect(jsonPath("$.data[1].role").value("MANAGER"))
                .andExpect(jsonPath("$.data[1].joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[2].membershipId").value(second.getId()));
    }

    @Test
    void 매니저와_외부인은_멤버십을_조회하거나_제거할_수_없다() throws Exception {
        Member owner = saveMember("membership-denied-owner@example.com", "소유자");
        Member manager = saveMember("membership-denied-manager@example.com", "매니저");
        Member outsider = saveMember("membership-denied-outsider@example.com", "외부인");
        Store store = saveActiveBusinessStore(owner, "권한 상점");
        StoreMembership managerMembership = storeMembershipRepository.save(StoreMembership.createManager(store, manager));

        assertMembershipListOwnerRequired(store, manager);
        assertMembershipListOwnerRequired(store, outsider);
        assertMembershipDeleteOwnerRequired(store, managerMembership.getId(), manager);
        assertMembershipDeleteOwnerRequired(store, managerMembership.getId(), outsider);
    }

    @Test
    void 소유자는_매니저를_제거하고_제거된_매니저는_즉시_운영_권한을_잃는다() throws Exception {
        Member owner = saveMember("membership-remove-owner@example.com", "소유자");
        Member manager = saveMember("membership-remove-manager@example.com", "매니저");
        Store store = saveActiveBusinessStore(owner, "제거 상점");
        StoreMembership membership = storeMembershipRepository.save(StoreMembership.createManager(store, manager));

        mockMvc.perform(delete("/api/store-operations/{storeId}/memberships/{membershipId}",
                        store.getId(), membership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/store-operations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        assertStoreAccessDenied("/api/store-operations/{storeId}/products", store.getId(), manager);
    }

    @Test
    void 소유자_멤버십은_제거할_수_없고_없는_대상과_다른_상점_대상은_노출하지_않는다() throws Exception {
        Member owner = saveMember("membership-protected-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "보호 상점");
        StoreMembership ownerMembership = storeMembershipRepository
                .findByStoreIdAndMemberId(store.getId(), owner.getId())
                .orElseThrow();
        Member inactiveManager = saveMember("membership-protected-inactive@example.com", "비활성 매니저");
        StoreMembership inactiveMembership = StoreMembership.createManager(store, inactiveManager);
        inactiveMembership.deactivate();
        storeMembershipRepository.save(inactiveMembership);
        Member otherOwner = saveMember("membership-protected-other@example.com", "다른 소유자");
        Member otherManager = saveMember("membership-protected-other-manager@example.com", "다른 매니저");
        Store otherStore = saveActiveBusinessStore(otherOwner, "다른 상점");
        StoreMembership otherMembership = storeMembershipRepository.save(StoreMembership.createManager(otherStore, otherManager));

        performMembershipDelete(store, ownerMembership.getId(), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_OWNER_MEMBERSHIP_PROTECTED"));
        performMembershipDelete(store, Long.MAX_VALUE, owner)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
        performMembershipDelete(store, inactiveMembership.getId(), owner)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
        performMembershipDelete(store, otherMembership.getId(), owner)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void 활성_소유자와_매니저는_운영_상점_목록을_보고_비활성_멤버십은_제외한다() throws Exception {
        Member operator = saveMember("operator-list@example.com", "운영자");
        Store personal = savePersonalStore(operator, "개인 상점");
        Member businessOwner = saveMember("operator-list-business-owner@example.com", "사업자 소유자");
        Store business = saveActiveBusinessStore(businessOwner, "사업자 상점");
        storeMembershipRepository.save(StoreMembership.createManager(business, operator));
        Member inactiveOwner = saveMember("operator-list-inactive-owner@example.com", "비활성 소유자");
        Store inactiveStore = saveActiveBusinessStore(inactiveOwner, "제외 상점");
        StoreMembership inactiveMembership = StoreMembership.createManager(inactiveStore, operator);
        inactiveMembership.deactivate();
        storeMembershipRepository.save(inactiveMembership);

        mockMvc.perform(get("/api/store-operations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].storeId").value(personal.getId()))
                .andExpect(jsonPath("$.data[0].type").value("PERSONAL"))
                .andExpect(jsonPath("$.data[0].publicName").value("개인 상점"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[1].storeId").value(business.getId()))
                .andExpect(jsonPath("$.data[1].role").value("MANAGER"))
                .andExpect(jsonPath("$.data[0].legalBusinessName").doesNotExist())
                .andExpect(jsonPath("$.data[0].businessRegistrationId").doesNotExist())
                .andExpect(jsonPath("$.data[0].rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.data[0].reviewCount").doesNotExist());
    }

    @Test
    void 소유자와_매니저는_요약과_카탈로그를_읽고_외부_회원은_거부된다() throws Exception {
        Member owner = saveMember("operator-owner@example.com", "소유자");
        Member manager = saveMember("operator-manager@example.com", "매니저");
        Member outsider = saveMember("operator-outsider@example.com", "외부인");
        Store store = saveActiveBusinessStore(owner, "운영 상점");
        storeMembershipRepository.save(StoreMembership.createManager(store, manager));
        saveProduct(store, "판매 상품");
        Product hidden = saveProduct(store, "숨김 상품");
        changeStatus(hidden, "HIDDEN");

        assertReadableSummary(owner, store);
        assertReadableSummary(manager, store);

        mockMvc.perform(get("/api/store-operations/{storeId}/products", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        assertStoreAccessDenied("/api/store-operations/{storeId}/summary", store.getId(), outsider);
        assertStoreAccessDenied("/api/store-operations/{storeId}/products", store.getId(), outsider);
    }

    @Test
    void 중단된_상점도_운영자는_읽을_수_있지만_카탈로그는_쓸_수_없다() throws Exception {
        Member owner = saveMember("suspended-operator-owner@example.com", "소유자");
        Member manager = saveMember("suspended-operator-manager@example.com", "매니저");
        Store store = saveActiveBusinessStore(owner, "중단 상점");
        storeMembershipRepository.save(StoreMembership.createManager(store, manager));
        saveProduct(store, "중단 전 상품");
        store.suspend();
        storeRepository.save(store);

        mockMvc.perform(get("/api/store-operations/{storeId}/summary", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onSaleCount").value(1))
                .andExpect(jsonPath("$.data.catalogWritable").value(false));

        mockMvc.perform(get("/api/store-operations/{storeId}/products", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 운영_카탈로그는_전체_상태와_공백을_제거한_검색어를_지원한다() throws Exception {
        Member owner = saveMember("catalog-filter-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "필터 상점");
        Product onSale = saveProduct(store, "  달콤한 사탕  ");
        Product reserved = saveProduct(store, "예약 상품");
        changeStatus(reserved, "RESERVED");
        Product soldOut = saveProduct(store, "판매완료 상품");
        changeStatus(soldOut, "SOLD_OUT");
        Product hidden = saveProduct(store, "숨김 사탕");
        changeStatus(hidden, "HIDDEN");
        addImages(hidden, "https://example.com/representative.jpg", "https://example.com/other.jpg");

        mockMvc.perform(get("/api/store-operations/{storeId}/products", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(4)))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value(hidden.getId()))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value("https://example.com/representative.jpg"))
                .andExpect(jsonPath("$.data.content[0].title").value("숨김 사탕"))
                .andExpect(jsonPath("$.data.content[0].status").value("HIDDEN"));

        mockMvc.perform(get("/api/store-operations/{storeId}/products", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .queryParam("status", "ON_SALE")
                        .queryParam("keyword", "  달콤한  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(onSale.getId()));
    }

    @Test
    void 운영_카탈로그는_ID_최신순과_오래된순_페이징을_지원하고_크기를_검증한다() throws Exception {
        Member owner = saveMember("catalog-page-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "페이징 상점");
        Product oldest = saveProduct(store, "첫 상품");
        saveProduct(store, "둘째 상품");
        Product newest = saveProduct(store, "셋째 상품");

        mockMvc.perform(get("/api/store-operations/{storeId}/products", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .queryParam("sort", "NEWEST")
                        .queryParam("page", "1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(oldest.getId()))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get("/api/store-operations/{storeId}/products", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .queryParam("sort", "OLDEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productId").value(oldest.getId()))
                .andExpect(jsonPath("$.data.content[2].productId").value(newest.getId()));

        mockMvc.perform(get("/api/store-operations/{storeId}/products", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 소유자는_상품을_숨기고_매니저는_숨긴_상품을_다시_노출한다() throws Exception {
        Member owner = saveMember("catalog-command-owner@example.com", "소유자");
        Member manager = saveMember("catalog-command-manager@example.com", "매니저");
        Store store = saveActiveBusinessStore(owner, "명령 상점");
        storeMembershipRepository.save(StoreMembership.createManager(store, manager));
        Product first = saveProduct(store, "첫 상품");
        Product second = saveProduct(store, "둘째 상품");

        performCommand("hide", store, owner, first.getId(), second.getId())
                .andExpect(status().isOk());
        assertStatus(first, "HIDDEN");
        assertStatus(second, "HIDDEN");

        performCommand("show", store, manager, first.getId(), second.getId())
                .andExpect(status().isOk());
        assertStatus(first, "ON_SALE");
        assertStatus(second, "ON_SALE");
    }

    @Test
    void 외부_회원과_중단된_상점의_운영자는_카탈로그를_변경할_수_없다() throws Exception {
        Member owner = saveMember("catalog-denied-owner@example.com", "소유자");
        Member outsider = saveMember("catalog-denied-outsider@example.com", "외부인");
        Store store = saveActiveBusinessStore(owner, "권한 상점");
        Product product = saveProduct(store, "상품");

        performCommand("hide", store, outsider, product.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));

        store.suspend();
        storeRepository.saveAndFlush(store);
        performCommand("hide", store, owner, product.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
        assertStatus(product, "ON_SALE");
    }

    @Test
    void 상품_ID_목록은_비어있거나_중복되거나_50개를_초과할_수_없다() throws Exception {
        Member owner = saveMember("catalog-validation-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "검증 상점");
        Product product = saveProduct(store, "상품");

        performRawCommand("hide", store, owner, "{\"productIds\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        performCommand("hide", store, owner, product.getId(), product.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        String fiftyOneIds = LongStream.rangeClosed(1, 51)
                .mapToObj(Long::toString)
                .collect(Collectors.joining(","));
        performRawCommand("hide", store, owner, "{\"productIds\":[" + fiftyOneIds + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 상품_ID는_양수여야_한다() throws Exception {
        Member owner = saveMember("catalog-positive-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "양수 검증 상점");

        performRawCommand("hide", store, owner, "{\"productIds\":[0,-1]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 상품_ID_목록이_아닌_요청은_검증_오류로_응답한다() throws Exception {
        Member owner = saveMember("catalog-shape-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "형식 검증 상점");

        performRawCommand("hide", store, owner, "{\"productIds\":\"not-a-list\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 다른_상점_상품과_없는_상품은_상품_없음으로_응답한다() throws Exception {
        Member owner = saveMember("catalog-not-found-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "기준 상점");
        Member otherOwner = saveMember("catalog-not-found-other@example.com", "다른 소유자");
        Store otherStore = saveActiveBusinessStore(otherOwner, "다른 상점");
        Product otherProduct = saveProduct(otherStore, "다른 상품");

        performCommand("hide", store, owner, otherProduct.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        performCommand("hide", store, owner, Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertStatus(otherProduct, "ON_SALE");
    }

    @Test
    void 판매중이_아닌_상품은_숨길_수_없고_일괄_변경도_롤백된다() throws Exception {
        Member owner = saveMember("catalog-hide-state-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "숨김 상태 상점");
        Product onSale = saveProduct(store, "판매중");
        Product reserved = saveProduct(store, "예약중");
        Product soldOut = saveProduct(store, "판매완료");
        Product hidden = saveProduct(store, "숨김");
        changeStatus(reserved, "RESERVED");
        changeStatus(soldOut, "SOLD_OUT");
        changeStatus(hidden, "HIDDEN");

        performCommand("hide", store, owner, onSale.getId(), reserved.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));
        performCommand("hide", store, owner, soldOut.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));
        performCommand("hide", store, owner, hidden.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));
        assertStatus(onSale, "ON_SALE");
    }

    @Test
    void 숨김이_아닌_상품은_노출할_수_없고_일괄_변경도_롤백된다() throws Exception {
        Member owner = saveMember("catalog-show-state-owner@example.com", "소유자");
        Store store = saveActiveBusinessStore(owner, "노출 상태 상점");
        Product hidden = saveProduct(store, "숨김");
        Product onSale = saveProduct(store, "판매중");
        Product reserved = saveProduct(store, "예약중");
        Product soldOut = saveProduct(store, "판매완료");
        changeStatus(hidden, "HIDDEN");
        changeStatus(reserved, "RESERVED");
        changeStatus(soldOut, "SOLD_OUT");

        performCommand("show", store, owner, hidden.getId(), onSale.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));
        performCommand("show", store, owner, reserved.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));
        performCommand("show", store, owner, soldOut.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));
        assertStatus(hidden, "HIDDEN");
    }

    private void assertReadableSummary(Member member, Store store) throws Exception {
        mockMvc.perform(get("/api/store-operations/{storeId}/summary", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onSaleCount").value(1))
                .andExpect(jsonPath("$.data.reservedCount").value(0))
                .andExpect(jsonPath("$.data.soldOutCount").value(0))
                .andExpect(jsonPath("$.data.hiddenCount").value(1))
                .andExpect(jsonPath("$.data.catalogWritable").value(true));
    }

    private void assertStoreAccessDenied(String path, Long storeId, Member member) throws Exception {
        mockMvc.perform(get(path, storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    private void assertMembershipListOwnerRequired(Store store, Member member) throws Exception {
        mockMvc.perform(get("/api/store-operations/{storeId}/memberships", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_OWNER_REQUIRED"));
    }

    private void assertMembershipDeleteOwnerRequired(Store store, Long membershipId, Member member) throws Exception {
        performMembershipDelete(store, membershipId, member)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_OWNER_REQUIRED"));
    }

    private org.springframework.test.web.servlet.ResultActions performMembershipDelete(
            Store store,
            Long membershipId,
            Member member
    ) throws Exception {
        return mockMvc.perform(delete("/api/store-operations/{storeId}/memberships/{membershipId}",
                        store.getId(), membershipId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private org.springframework.test.web.servlet.ResultActions performCommand(
            String command,
            Store store,
            Member member,
            Long... productIds
    ) throws Exception {
        String ids = java.util.Arrays.stream(productIds)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return performRawCommand(command, store, member, "{\"productIds\":[" + ids + "]}");
    }

    private org.springframework.test.web.servlet.ResultActions performRawCommand(
            String command,
            Store store,
            Member member,
            String content
    ) throws Exception {
        return mockMvc.perform(post("/api/store-operations/{storeId}/products/{command}", store.getId(), command)
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content));
    }

    private void assertStatus(Product product, String expectedStatus) {
        String actualStatus = jdbcTemplate.queryForObject(
                "select status from products where id = ?",
                String.class,
                product.getId()
        );
        org.assertj.core.api.Assertions.assertThat(actualStatus).isEqualTo(expectedStatus);
    }

    private Store savePersonalStore(Member owner, String publicName) {
        Store store = storeRepository.save(Store.createPersonal(owner, publicName, "소개"));
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        return store;
    }

    private Store saveActiveBusinessStore(Member owner, String publicName) {
        Store store = Store.applyBusiness(owner, publicName, "소개", "비공개 법인", "999-99-99999");
        store.approve();
        store = storeRepository.save(store);
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        return store;
    }

    private Product saveProduct(Store store, String title) {
        return transactionTemplate.execute(status -> {
            Product product = Product.create(entityManager.find(Store.class, store.getId()), title, "설명", 10_000L);
            entityManager.persist(product);
            entityManager.flush();
            return product;
        });
    }

    private void changeStatus(Product product, String status) {
        jdbcTemplate.update("update products set status = ? where id = ?", status, product.getId());
    }

    private void addImages(Product product, String representativeUrl, String otherUrl) {
        transactionTemplate.executeWithoutResult(status -> {
            Product managedProduct = entityManager.find(Product.class, product.getId());
            managedProduct.addImage(representativeUrl);
            managedProduct.addImage(otherUrl);
            entityManager.flush();
        });
    }

    private Member saveMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), nickname));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
    }
}
