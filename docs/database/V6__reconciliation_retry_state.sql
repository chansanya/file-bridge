ALTER TABLE fb_reconciliation_issue
  ADD COLUMN fingerprint VARCHAR(255) NULL AFTER id,
  ADD COLUMN lease_owner VARCHAR(200) NULL AFTER next_attempt_at,
  ADD COLUMN lease_until DATETIME(6) NULL AFTER lease_owner,
  ADD COLUMN resolved_at DATETIME(6) NULL AFTER updated_at,
  ADD UNIQUE KEY uk_fb_reconcile_fingerprint(fingerprint),
  ADD KEY idx_fb_reconcile_lease(status, next_attempt_at, lease_until);
