package com.sweet.market.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.Store;

class InventoryTest {

    @Test
    void 재고를_초기화하면_사용가능수량은_총수량과_같다() {
        Inventory inventory = Inventory.initialize(재고형_상품(), 3);

        assertThat(inventory.getTotalQuantity()).isEqualTo(3);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);
    }

    @Test
    void 재고를_초기화하면_초기화_이력을_생성한다() {
        Inventory inventory = Inventory.initialize(재고형_상품(), 3);

        InventoryAdjustment adjustment = inventory.getInitializationAdjustment();

        assertThat(adjustment.getChangeType()).isEqualTo(InventoryChangeType.INITIALIZATION);
        assertThat(adjustment.getAfterTotalQuantity()).isEqualTo(3);
    }

    @Test
    void 재고를_예약하면_사용가능수량이_감소한다() {
        Product product = 재고형_상품();
        Inventory inventory = Inventory.initialize(product, 3);

        inventory.reserve(주문(product));

        assertThat(inventory.getTotalQuantity()).isEqualTo(3);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(2);
    }

    @Test
    void 예약을_해제하면_사용가능수량이_복구된다() {
        Product product = 재고형_상품();
        Inventory inventory = Inventory.initialize(product, 3);
        Order order = 주문(product);
        inventory.reserve(order);

        InventoryAdjustment adjustment = inventory.release(order);

        assertThat(inventory.getTotalQuantity()).isEqualTo(3);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);
        assertThat(adjustment.getChangeType()).isEqualTo(InventoryChangeType.RELEASE);
        assertThat(adjustment.getOrder()).isSameAs(order);
        assertThat(adjustment.getBeforeReservedQuantity()).isEqualTo(1);
        assertThat(adjustment.getAfterReservedQuantity()).isZero();
    }

    @Test
    void 배송을_확정하면_예약과_총수량이_함께_감소한다() {
        Product product = 재고형_상품();
        Inventory inventory = Inventory.initialize(product, 3);
        Order order = 주문(product);
        inventory.reserve(order);

        inventory.commitShipment(order);

        assertThat(inventory.getTotalQuantity()).isEqualTo(2);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(2);
    }

    @Test
    void 재고는_예약보다_낮은_총수량으로_조정할_수_없다() {
        Product product = 재고형_상품();
        Inventory inventory = Inventory.initialize(product, 3);
        inventory.reserve(주문(product));

        assertThatThrownBy(() -> inventory.adjust(0, InventoryAdjustmentReason.STOCKTAKE, null, 운영자()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 재고를_조정하면_변경된_총수량으로_사용가능수량을_계산한다() {
        Inventory inventory = Inventory.initialize(재고형_상품(), 3);

        inventory.adjust(5, InventoryAdjustmentReason.RESTOCK, "입고전표-7", 운영자());

        assertThat(inventory.getTotalQuantity()).isEqualTo(5);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
    }

    private Product 재고형_상품() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");
        Store store = Store.createPersonal(owner, "상점", "");
        return Product.create(store, "상품", "설명", 10_000L, ProductSalesPolicy.STOCK_MANAGED, 3, 3);
    }

    private Order 주문(Product product) {
        return Order.create(Member.create("buyer@example.com", "encoded-password", "buyer"), product);
    }

    private Member 운영자() {
        return Member.create("operator@example.com", "encoded-password", "operator");
    }
}
