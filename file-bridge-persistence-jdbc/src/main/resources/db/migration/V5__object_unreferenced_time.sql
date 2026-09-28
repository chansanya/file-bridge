ALTER TABLE fb_storage_object
  ADD COLUMN unreferenced_at DATETIME(6) NULL AFTER delete_after,
  ADD KEY idx_fb_object_unreferenced(status, unreferenced_at);
