package com.sweet.market.purchase;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.purchase.application.ProductReservationService;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductReservationServiceTest extends IntegrationTestSupport {

    @Autowired
    private ProductReservationService reservationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 재고형_상품은_가용수량이_있을때만_한개를_예약한다() {
        Order order = createPersistedStockOrder(1);

        reservationService.reserve(order);

        assertThat(availableQuantity(order.getProduct().getId())).isZero();
        assertThat(countInventoryAdjustments(order.getId(), "RESERVATION")).isOne();
        assertThat(reservationBeforeQuantity(order.getId())).isZero();
        assertThat(reservationAfterQuantity(order.getId())).isEqualTo(1);
    }

    @Test
    void 단품은_판매중일때_한번만_예약한다() {
        Order winner = createPersistedSingleItemOrder();
        Order loser = createPersistedSingleItemOrderForAnotherBuyer(winner.getProduct());

        reservationService.reserve(winner);

        assertThatThrownBy(() -> reservationService.reserve(loser))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);
    }

    @Test
    void 재고가_없으면_예약_이력을_남기지_않는다() {
        Order order = createPersistedStockOrder(0);

        assertThatThrownBy(() -> reservationService.reserve(order))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);

        assertThat(countInventoryAdjustments(order.getId(), "RESERVATION")).isZero();
    }

    private Order createPersistedStockOrder(int totalQuantity) {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("stock-seller@example.com", "encoded-password", "판매자"));
            Member buyer = memberRepository.save(Member.create("stock-buyer@example.com", "encoded-password", "구매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "재고 상점", "소개"));
            Product product = productRepository.save(Product.create(
                    store,
                    "재고 상품",
                    "설명",
                    10_000L,
                    ProductSalesPolicy.STOCK_MANAGED,
                    1,
                    totalQuantity
            ));
            inventoryRepository.save(Inventory.initialize(product, totalQuantity));
            return orderRepository.saveAndFlush(Order.create(buyer, product));
        });
    }

    private Order createPersistedSingleItemOrder() {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("single-seller@example.com", "encoded-password", "판매자"));
            Member buyer = memberRepository.save(Member.create("single-buyer@example.com", "encoded-password", "구매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "단품 상점", "소개"));
            Product product = productRepository.save(Product.create(store, "단품 상품", "설명", 10_000L));
            Order order = orderRepository.save(Order.create(buyer, product));
            product.restoreOnSaleFromReservation();
            return orderRepository.saveAndFlush(order);
        });
    }

    private Order createPersistedSingleItemOrderForAnotherBuyer(Product product) {
        return transactionTemplate.execute(status -> {
            Member buyer = memberRepository.save(Member.create("single-other-buyer@example.com", "encoded-password", "다른 구매자"));
            Product persistedProduct = productRepository.findById(product.getId()).orElseThrow();
            Order order = orderRepository.save(Order.create(buyer, persistedProduct));
            persistedProduct.restoreOnSaleFromReservation();
            return orderRepository.saveAndFlush(order);
        });
    }

    private int availableQuantity(Long productId) {
        return jdbcTemplate.queryForObject(
                "select total_quantity - reserved_quantity from inventories where product_id = ?",
                Integer.class,
                productId
        );
    }

    private long countInventoryAdjustments(Long orderId, String changeType) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from inventory_adjustments where order_id = ? and change_type = ?",
                Long.class,
                orderId,
                changeType
        );
        return count == null ? 0 : count;
    }

    private int reservationBeforeQuantity(Long orderId) {
        return jdbcTemplate.queryForObject(
                "select before_reserved_quantity from inventory_adjustments where order_id = ? and change_type = 'RESERVATION'",
                Integer.class,
                orderId
        );
    }

    private int reservationAfterQuantity(Long orderId) {
        return jdbcTemplate.queryForObject(
                "select after_reserved_quantity from inventory_adjustments where order_id = ? and change_type = 'RESERVATION'",
                Integer.class,
                orderId
        );
    }
}
