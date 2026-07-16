package com.sweet.market.coupon.application;

import com.sweet.market.coupon.domain.CouponReservation;
import com.sweet.market.coupon.domain.CouponReservationStatus;
import com.sweet.market.coupon.repository.CouponReservationRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;

class CouponReservationExpiryTransactionServiceTest {

    @Test
    void 만료_처리는_주문을_먼저_잠근_뒤_예약을_잠근다() {
        CouponReservationRepository reservationRepository = mock(CouponReservationRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        InventoryService inventoryService = mock(InventoryService.class);
        CouponReservationExpiryTransactionService service = new CouponReservationExpiryTransactionService(
                reservationRepository, orderRepository, inventoryService
        );
        CouponReservation reservation = mock(CouponReservation.class);
        Order order = mock(Order.class);
        Instant now = Instant.parse("2026-07-15T00:00:00Z");

        when(reservationRepository.findOrderIdByReservationId(10L)).thenReturn(Optional.of(20L));
        when(orderRepository.findStateChangeTargetById(20L)).thenReturn(Optional.of(order));
        when(reservationRepository.findActiveByOrderIdForUpdate(20L)).thenReturn(Optional.of(reservation));
        when(reservation.getStatus()).thenReturn(CouponReservationStatus.RESERVED);
        when(reservation.getExpiresAt()).thenReturn(now);
        when(order.getStatus()).thenReturn(OrderStatus.CREATED);

        service.expireReservation(10L, now);

        InOrder locks = inOrder(orderRepository, reservationRepository);
        locks.verify(orderRepository).findStateChangeTargetById(20L);
        locks.verify(reservationRepository).findActiveByOrderIdForUpdate(20L);
    }
}
