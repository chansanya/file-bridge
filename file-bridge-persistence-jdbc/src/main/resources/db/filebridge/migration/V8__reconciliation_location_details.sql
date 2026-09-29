ALTER TABLE fb_reconciliation_issue
  ADD COLUMN bucket_name VARCHAR(255) NULL AFTER storage_id,
  ADD COLUMN provider_upload_id VARCHAR(1024) NULL AFTER object_key;
