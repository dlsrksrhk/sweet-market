CREATE TABLE coupon_campaigns (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    owner_type VARCHAR(20) NOT NULL,
    store_id BIGINT,
    scope VARCHAR(30) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value BIGINT NOT NULL,
    max_discount_amount BIGINT,
    minimum_purchase_amount BIGINT NOT NULL,
    stackable BOOLEAN NOT NULL,
    title VARCHAR(100) NOT NULL,
    label VARCHAR(200),
    issue_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    issue_ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    validity_type VARCHAR(30) NOT NULL,
    common_expires_at TIMESTAMP WITH TIME ZONE,
    validity_days INTEGER,
    lifecycle_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_coupon_campaigns_owner CHECK (
        (owner_type = 'PLATFORM' AND store_id IS NULL)
        OR (owner_type = 'STORE' AND store_id IS NOT NULL)
    ),
    CONSTRAINT chk_coupon_campaigns_scope CHECK (scope IN ('ALL_PRODUCTS', 'SELECTED_PRODUCTS')),
    CONSTRAINT chk_coupon_campaigns_discount_type CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_coupon_campaigns_discount_amounts CHECK (
        discount_value >= 0
        AND minimum_purchase_amount >= 0
        AND (
            (discount_type = 'PERCENTAGE' AND max_discount_amount IS NOT NULL AND max_discount_amount >= 0)
            OR (discount_type = 'FIXED_AMOUNT' AND max_discount_amount IS NULL)
        )
    ),
    CONSTRAINT chk_coupon_campaigns_issue_period CHECK (issue_starts_at < issue_ends_at),
    CONSTRAINT chk_coupon_campaigns_validity CHECK (
        (validity_type = 'COMMON_EXPIRY'
            AND common_expires_at IS NOT NULL
            AND validity_days IS NULL
            AND common_expires_at >= issue_ends_at)
        OR (validity_type = 'DAYS_FROM_ISSUANCE'
            AND common_expires_at IS NULL
            AND validity_days IS NOT NULL
            AND validity_days > 0)
    ),
    CONSTRAINT chk_coupon_campaigns_lifecycle_status CHECK (lifecycle_status IN ('DRAFT', 'SCHEDULED', 'PAUSED', 'ENDED')),
    CONSTRAINT fk_coupon_campaigns_store FOREIGN KEY (store_id) REFERENCES stores (id)
);

CREATE TABLE coupon_campaign_targets (
    id BIGSERIAL PRIMARY KEY,
    coupon_campaign_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT uq_coupon_campaign_targets_campaign_product UNIQUE (coupon_campaign_id, product_id),
    CONSTRAINT fk_coupon_campaign_targets_campaign FOREIGN KEY (coupon_campaign_id) REFERENCES coupon_campaigns (id),
    CONSTRAINT fk_coupon_campaign_targets_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE member_coupons (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    coupon_campaign_id BIGINT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value BIGINT NOT NULL,
    max_discount_amount BIGINT,
    minimum_purchase_amount BIGINT NOT NULL,
    scope VARCHAR(30) NOT NULL,
    stackable BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_member_coupons_campaign_member UNIQUE (coupon_campaign_id, member_id),
    CONSTRAINT chk_member_coupons_discount_type CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_member_coupons_discount_values CHECK (discount_value >= 0 AND minimum_purchase_amount >= 0),
    CONSTRAINT chk_member_coupons_scope CHECK (scope IN ('ALL_PRODUCTS', 'SELECTED_PRODUCTS')),
    CONSTRAINT chk_member_coupons_status CHECK (status IN ('ISSUED', 'USED')),
    CONSTRAINT fk_member_coupons_campaign FOREIGN KEY (coupon_campaign_id) REFERENCES coupon_campaigns (id)
);

DO $$
BEGIN
    IF to_regclass('public.members') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_member_coupons_member') THEN
        ALTER TABLE member_coupons
            ADD CONSTRAINT fk_member_coupons_member
            FOREIGN KEY (member_id) REFERENCES members (id);
    END IF;
END $$;

CREATE INDEX idx_coupon_campaigns_owner_lifecycle_issue_period
    ON coupon_campaigns (owner_type, store_id, lifecycle_status, issue_starts_at, issue_ends_at);
CREATE INDEX idx_coupon_campaign_targets_product_campaign
    ON coupon_campaign_targets (product_id, coupon_campaign_id);
CREATE INDEX idx_member_coupons_member_status_valid_until_id
    ON member_coupons (member_id, status, valid_until, id);
