ALTER TABLE coupon_campaigns
    ADD COLUMN issue_limit INTEGER,
    ADD COLUMN issued_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_coupon_campaigns_issue_limit
        CHECK (issue_limit IS NULL OR issue_limit > 0),
    ADD CONSTRAINT chk_coupon_campaigns_issued_count
        CHECK (issued_count >= 0 AND (issue_limit IS NULL OR issued_count <= issue_limit));
