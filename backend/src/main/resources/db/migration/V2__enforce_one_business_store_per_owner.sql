-- Preserve every existing BUSINESS store. Operators must resolve duplicates before retrying this migration.
DO
$$
BEGIN
    IF
EXISTS (
        SELECT 1
        FROM stores
        WHERE type = 'BUSINESS'
        GROUP BY owner_member_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate BUSINESS stores exist for at least one owner; resolve duplicates before applying V2';
END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_business_store_owner
    ON stores (owner_member_id)
    WHERE type = 'BUSINESS';
