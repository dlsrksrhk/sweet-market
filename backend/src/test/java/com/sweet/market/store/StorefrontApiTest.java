package com.sweet.market.store;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.review.domain.Review;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

class StorefrontApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 활성_상점_헤더는_현재_상품_기준의_평점과_공개_상품_수만_노출한다() throws Exception {
        Store store = saveActiveBusinessStore("active-storefront@example.com");
        Member historicalSeller = saveMember("historical-seller@example.com", "과거 판매자");
        Member firstBuyer = saveMember("first-buyer@example.com", "구매자1");
        Member secondBuyer = saveMember("second-buyer@example.com", "구매자2");
        Product firstReviewed = saveProduct(store, "리뷰 상품 1");
        Product secondReviewed = saveProduct(store, "리뷰 상품 2");
        saveProduct(store, "판매 상품");
        Product hiddenProduct = saveProduct(store, "숨김 상품");
        jdbcTemplate.update("update products set status = 'HIDDEN' where id = ?", hiddenProduct.getId());
        saveReview(firstBuyer, firstReviewed, 4);
        saveReview(secondBuyer, secondReviewed, 5);
        jdbcTemplate.update("update reviews set seller_id = ?", historicalSeller.getId());

        mockMvc.perform(get("/api/stores/{storeId}", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatingStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.publicProductCount").value(3))
                .andExpect(jsonPath("$.data.legalBusinessName").doesNotExist())
                .andExpect(jsonPath("$.data.businessRegistrationId").doesNotExist())
                .andExpect(jsonPath("$.data.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.data.ownerMemberId").doesNotExist())
                .andExpect(jsonPath("$.data.memberships").doesNotExist());
    }

    @Test
    void 리뷰가_없는_활성_상점은_평점이_없고_리뷰_수가_0이다() throws Exception {
        Store store = saveActiveBusinessStore("no-reviews@example.com");

        mockMvc.perform(get("/api/stores/{storeId}", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value((Object) null))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
    }

    @Test
    void 중단된_상점은_공개_정체성과_0으로_초기화된_집계만_노출한다() throws Exception {
        Store store = saveActiveBusinessStore("suspended-storefront@example.com");
        Product product = saveProduct(store, "중단 전 상품");
        saveReview(saveMember("suspended-buyer@example.com", "구매자"), product, 5);
        store.suspend();
        storeRepository.save(store);

        mockMvc.perform(get("/api/stores/{storeId}", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(store.getId()))
                .andExpect(jsonPath("$.data.type").value("BUSINESS"))
                .andExpect(jsonPath("$.data.publicName").value("공개 상점"))
                .andExpect(jsonPath("$.data.introduction").value("공개 소개"))
                .andExpect(jsonPath("$.data.operatingStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.averageRating").value((Object) null))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.publicProductCount").value(0));
    }

    @Test
    void 승인_대기_상점은_공개_헤더로_조회할_수_없다() throws Exception {
        Store store = storeRepository.save(Store.applyBusiness(
                saveMember("pending-storefront@example.com", "소유자"),
                "대기 상점", "대기 소개", "대기 법인", "111-11-11111"
        ));

        assertStoreNotFound(store.getId());
    }

    @Test
    void 거절된_상점은_공개_헤더로_조회할_수_없다() throws Exception {
        Store store = Store.applyBusiness(
                saveMember("rejected-storefront@example.com", "소유자"),
                "거절 상점", "거절 소개", "거절 법인", "222-22-22222"
        );
        store.reject("서류 미비");
        storeRepository.save(store);

        assertStoreNotFound(store.getId());
    }

    @Test
    void 존재하지_않는_상점은_공개_헤더로_조회할_수_없다() throws Exception {
        assertStoreNotFound(999_999L);
    }

    private Store saveActiveBusinessStore(String email) {
        Store store = Store.applyBusiness(
                saveMember(email, "소유자"),
                "공개 상점", "공개 소개", "비공개 법인", "999-99-99999"
        );
        store.approve();
        return storeRepository.save(store);
    }

    private Product saveProduct(Store store, String title) {
        return transactionTemplate.execute(status -> {
            Store managedStore = entityManager.find(Store.class, store.getId());
            Product product = Product.create(managedStore, title, "설명", 10_000L);
            entityManager.persist(product);
            entityManager.flush();
            return product;
        });
    }

    private void saveReview(Member buyer, Product product, int rating) {
        transactionTemplate.executeWithoutResult(status -> {
            Member managedBuyer = entityManager.find(Member.class, buyer.getId());
            Product managedProduct = entityManager.find(Product.class, product.getId());
            Order order = Order.create(managedBuyer, managedProduct);
            entityManager.persist(order);
            entityManager.persist(Review.create(order, rating, "리뷰"));
            entityManager.flush();
        });
    }

    private Member saveMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), nickname));
    }

    private void assertStoreNotFound(Long storeId) throws Exception {
        mockMvc.perform(get("/api/stores/{storeId}", storeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }
}
