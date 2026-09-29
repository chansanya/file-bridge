ALTER TABLE fb_storage_object
  DROP INDEX uk_fb_object_location,
  ADD COLUMN bucket_name_normalized VARCHAR(255)
    GENERATED ALWAYS AS (COALESCE(bucket_name, '')) STORED AFTER bucket_name,
  ADD COLUMN object_key_hash BINARY(32)
    GENERATED ALWAYS AS (UNHEX(SHA2(object_key, 256))) STORED AFTER object_key,
  ADD UNIQUE KEY uk_fb_object_location(storage_id, bucket_name_normalized, object_key_hash);
