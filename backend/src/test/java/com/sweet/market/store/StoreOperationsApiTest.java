package com.sweet.market.store;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
