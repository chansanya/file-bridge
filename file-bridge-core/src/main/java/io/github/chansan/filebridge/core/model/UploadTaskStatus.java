package io.github.chansan.filebridge.core.model;

/** 分片上传任务状态。 */
public enum UploadTaskStatus {
  CREATED,
  UPLOADING,
  COMPLETING,
  VERIFYING,
  COMPLETED,
  CANCELLED,
  EXPIRED,
  FAILED
}
