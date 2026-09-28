CREATE TABLE fb_storage_object (
 id CHAR(36) PRIMARY KEY, storage_id VARCHAR(100) NOT NULL, bucket_name VARCHAR(255), object_key VARCHAR(1024) NOT NULL, size_bytes BIGINT NOT NULL, sha256 CHAR(64), content_type VARCHAR(255), status VARCHAR(32) NOT NULL, verified_at DATETIME(6), created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0, delete_after DATETIME(6), last_error VARCHAR(1000), UNIQUE KEY uk_fb_object_location(storage_id, bucket_name(128), object_key(512)), KEY idx_fb_object_digest(storage_id,status,sha256,size_bytes), KEY idx_fb_object_cleanup(status,delete_after)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE fb_file_reference (
 id CHAR(36) PRIMARY KEY, object_id CHAR(36) NOT NULL, tenant_id VARCHAR(128) NOT NULL, owner_id VARCHAR(128) NOT NULL, original_name VARCHAR(512) NOT NULL, business_type VARCHAR(128), business_id VARCHAR(256), status VARCHAR(32) NOT NULL, created_at DATETIME(6) NOT NULL, deleted_at DATETIME(6), CONSTRAINT fk_fb_ref_object FOREIGN KEY(object_id) REFERENCES fb_storage_object(id), KEY idx_fb_ref_actor(tenant_id,owner_id,status,created_at), KEY idx_fb_ref_object(object_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE fb_idempotency_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, owner_id VARCHAR(128) NOT NULL, operation_name VARCHAR(64) NOT NULL, idempotency_key VARCHAR(200) NOT NULL, request_hash CHAR(64) NOT NULL, response_value VARCHAR(1024) NOT NULL, expires_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_fb_idempotency(tenant_id,owner_id,operation_name,idempotency_key), KEY idx_fb_idempotency_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
