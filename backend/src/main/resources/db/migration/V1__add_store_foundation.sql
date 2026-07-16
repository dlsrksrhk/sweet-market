CREATE TABLE IF NOT EXISTS stores
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    version
    BIGINT
    NOT
    NULL
    DEFAULT
    0,
    owner_member_id
    BIGINT
    NOT
    NULL,
    type
    VARCHAR
(
    20
) NOT NULL,
    public_name VARCHAR
(
    100
) NOT NULL,
    introduction VARCHAR
(
    2000
) NOT NULL,
    legal_business_name VARCHAR
(
    120
),
    business_registration_id VARCHAR
(
    40
),
    status VARCHAR
(
    20
) NOT NULL,
    rejection_reason VARCHAR
(
    1000
),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stores_type CHECK
(
    type
    IN
(
    'PERSONAL',
    'BUSINESS'
)),
    CONSTRAINT chk_stores_status CHECK
(
    status
    IN
(
    'PENDING',
    'ACTIVE',
    'REJECTED',
    'SUSPENDED'
))
    );

ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS store_memberships
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    store_id
    BIGINT
    NOT
    NULL,
    member_id
    BIGINT
    NOT
    NULL,
    role
    VARCHAR
(
    20
) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_memberships_store_member UNIQUE
(
    store_id,
    member_id
),
    CONSTRAINT chk_store_memberships_role CHECK
(
    role
    IN
(
    'OWNER',
    'MANAGER'
))
    );

CREATE INDEX IF NOT EXISTS idx_stores_owner_member_id ON stores (owner_member_id);
CREATE INDEX IF NOT EXISTS idx_stores_type_status ON stores (type, status);
CREATE INDEX IF NOT EXISTS idx_store_memberships_member_id_active ON store_memberships (member_id, active);
CREATE INDEX IF NOT EXISTS idx_store_memberships_store_id_active ON store_memberships (store_id, active);
CREATE UNIQUE INDEX IF NOT EXISTS uq_personal_store_owner
    ON stores (owner_member_id)
    WHERE type = 'PERSONAL';

DO
$$
BEGIN
    IF
to_regclass('public.members') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_stores_owner_member') THEN
ALTER TABLE stores
    ADD CONSTRAINT fk_stores_owner_member
        FOREIGN KEY (owner_member_id) REFERENCES members (id);
END IF;

    IF
NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_store_memberships_store') THEN
ALTER TABLE store_memberships
    ADD CONSTRAINT fk_store_memberships_store
        FOREIGN KEY (store_id) REFERENCES stores (id);
END IF;

    IF
to_regclass('public.members') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_store_memberships_member') THEN
ALTER TABLE store_memberships
    ADD CONSTRAINT fk_store_memberships_member
        FOREIGN KEY (member_id) REFERENCES members (id);
END IF;
END $$;

DO
$$
BEGIN
    IF
to_regclass('public.members') IS NOT NULL THEN
        INSERT INTO stores (
            owner_member_id,
            type,
            public_name,
            introduction,
            status
        )
SELECT m.id,
       'PERSONAL',
       m.nickname || '의 상점',
       '',
       'ACTIVE'
FROM members m
WHERE m.role = 'MEMBER'
  AND NOT EXISTS (SELECT 1
                  FROM stores s
                  WHERE s.owner_member_id = m.id
                    AND s.type = 'PERSONAL');

IF
to_regclass('public.products') IS NOT NULL THEN
            INSERT INTO stores (
                owner_member_id,
                type,
                public_name,
                introduction,
                status
            )
SELECT DISTINCT m.id,
                'PERSONAL',
                m.nickname || '의 상점',
                '',
                'ACTIVE'
FROM members m
         JOIN products p ON p.seller_id = m.id
WHERE NOT EXISTS (SELECT 1
                  FROM stores s
                  WHERE s.owner_member_id = m.id
                    AND s.type = 'PERSONAL');
END IF;

INSERT INTO store_memberships (store_id, member_id, role, active)
SELECT s.id, s.owner_member_id, 'OWNER', TRUE
FROM stores s
WHERE s.type = 'PERSONAL'
  AND NOT EXISTS (SELECT 1
                  FROM store_memberships sm
                  WHERE sm.store_id = s.id
                    AND sm.member_id = s.owner_member_id);
END IF;
END $$;

DO
$$
BEGIN
    IF
to_regclass('public.products') IS NOT NULL THEN
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS store_id BIGINT;

UPDATE products p
SET store_id = s.id FROM stores s
WHERE p.store_id IS NULL
  AND p.seller_id = s.owner_member_id
  AND s.type = 'PERSONAL';

IF
NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_store') THEN
ALTER TABLE products
    ADD CONSTRAINT fk_products_store
        FOREIGN KEY (store_id) REFERENCES stores (id);
END IF;

CREATE INDEX IF NOT EXISTS idx_products_store_id ON products (store_id);
END IF;

    IF
to_regclass('public.orders') IS NOT NULL THEN
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS seller_id BIGINT;

IF
to_regclass('public.products') IS NOT NULL THEN
UPDATE orders o
SET seller_id = p.seller_id FROM products p
WHERE o.seller_id IS NULL
  AND o.product_id = p.id;
END IF;

        IF
to_regclass('public.members') IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_seller') THEN
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_seller
        FOREIGN KEY (seller_id) REFERENCES members (id);
END IF;

CREATE INDEX IF NOT EXISTS idx_orders_seller_id ON orders (seller_id);
END IF;
END $$;
