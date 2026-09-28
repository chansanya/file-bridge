CREATE TABLE fb_reconciliation_issue (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, issue_type VARCHAR(64) NOT NULL, storage_id VARCHAR(100), object_key VARCHAR(1024), entity_id VARCHAR(64), status VARCHAR(32) NOT NULL DEFAULT 'OPEN', attempts INT NOT NULL DEFAULT 0, last_error VARCHAR(1000), next_attempt_at DATETIME(6), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_fb_reconcile_work(status,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
