package com.sweet.market.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.inventory.domain.InventoryAdjustmentReason;
import com.sweet.market.inventory.domain.InventoryChangeType;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

class InventoryServiceTransactionTest extends IntegrationTestSupport {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMembershipRepository storeMembershipRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private InventoryAdjustmentRepository inventoryAdjustmentRepository;

    @Test
    void 감사_이력_저장에_실패하면_재고_변경도_롤백한다() {
        Member owner = memberRepository.save(Member.create(
                "inventory-audit-failure@example.com",
                "encoded-password",
                "소유자"
        ));
        Store store = Store.applyBusiness(owner, "감사 실패 상점", "소개", "법인", "777-77-77777");
        store.approve();
        store = storeRepository.save(store);
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        Long storeId = store.getId();
        Product product = transactionTemplate.execute(status -> {
            Product saved = Product.create(
                    entityManager.find(Store.class, storeId),
                    "재고 상품",
                    "설명",
                    10_000L,
                    ProductSalesPolicy.STOCK_MANAGED,
                    3,
                    5
            );
            entityManager.persist(saved);
            entityManager.flush();
            return saved;
        });
        inventoryService.initialize(product, 5, owner.getId());
        doThrow(new DataIntegrityViolationException("감사 저장 실패"))
                .when(inventoryAdjustmentRepository)
                .save(argThat(adjustment ->
                        adjustment.getChangeType() == InventoryChangeType.MANUAL_ADJUSTMENT));

        Long productId = product.getId();
        Long ownerId = owner.getId();
        assertThatThrownBy(() -> inventoryService.adjust(
                ownerId,
                storeId,
                productId,
                new InventoryAdjustmentRequest(9, InventoryAdjustmentReason.RESTOCK, null)
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select total_quantity from inventories where product_id = ?",
                Integer.class,
                productId
        )).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory_adjustments where product_id = ? and change_type = 'MANUAL_ADJUSTMENT'",
                Long.class,
                productId
        )).isZero();
    }
}
