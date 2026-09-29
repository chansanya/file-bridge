ALTER TABLE fb_upload_task
  ADD COLUMN completion_attempts INT NOT NULL DEFAULT 0 AFTER lease_until,
  ADD COLUMN next_attempt_at DATETIME(6) NULL AFTER completion_attempts,
  ADD COLUMN last_error VARCHAR(1000) NULL AFTER next_attempt_at,
  ADD KEY idx_fb_upload_completion(status, next_attempt_at, lease_until);
