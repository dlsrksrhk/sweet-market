CREATE UNIQUE INDEX IF NOT EXISTS uq_business_store_owner
    ON stores (owner_member_id)
    WHERE type = 'BUSINESS';
