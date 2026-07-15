ALTER TABLE coupon_campaigns
    DROP CONSTRAINT chk_coupon_campaigns_discount_amounts;

ALTER TABLE coupon_campaigns
    ADD CONSTRAINT chk_coupon_campaigns_discount_amounts CHECK (
        discount_value >= 0
        AND minimum_purchase_amount >= 0
        AND (
            (discount_type = 'PERCENTAGE' AND (max_discount_amount IS NULL OR max_discount_amount >= 0))
            OR (discount_type = 'FIXED_AMOUNT' AND max_discount_amount IS NULL)
        )
    );

CREATE TABLE member_coupon_target_products (
    member_coupon_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT pk_member_coupon_target_products PRIMARY KEY (member_coupon_id, product_id),
    CONSTRAINT fk_member_coupon_target_products_coupon
        FOREIGN KEY (member_coupon_id) REFERENCES member_coupons (id) ON DELETE CASCADE
);

CREATE INDEX idx_member_coupon_target_products_product_coupon
    ON member_coupon_target_products (product_id, member_coupon_id);
