CREATE TABLE coupon_reservations (
    id BIGSERIAL PRIMARY KEY,
    member_coupon_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    released_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_coupon_reservations_order UNIQUE (order_id),
    CONSTRAINT chk_coupon_reservations_status CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT fk_coupon_reservations_member_coupon FOREIGN KEY (member_coupon_id) REFERENCES member_coupons (id),
    CONSTRAINT fk_coupon_reservations_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE UNIQUE INDEX uq_coupon_reservations_active_member_coupon
    ON coupon_reservations (member_coupon_id)
    WHERE status = 'RESERVED';

ALTER TABLE orders
    ADD COLUMN member_coupon_id BIGINT REFERENCES member_coupons (id),
    ADD COLUMN coupon_discount_amount BIGINT NOT NULL DEFAULT 0;
