package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 服务端确认的上传分片记录。
 *
 * @param id 分片记录 ID
 * @param uploadId 上传任务 ID
 * @param partNumber 从 1 开始的分片序号
 * @param size 实际字节数
 * @param sha256 分片摘要
 * @param providerPartTag 平台分片标签
 * @param status 分片状态
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public final class UploadPart {
  private final UUID id;
  private final UUID uploadId;
  private final int partNumber;
  private final long size;
  private final String sha256;
  private final String providerPartTag;
  private final UploadPartStatus status;
  private final Instant createdAt;
  private final Instant updatedAt;

  public UploadPart(
      UUID id,
      UUID uploadId,
      int partNumber,
      long size,
      String sha256,
      String providerPartTag,
      UploadPartStatus status,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.uploadId = uploadId;
    this.partNumber = partNumber;
    this.size = size;
    this.sha256 = sha256;
    this.providerPartTag = providerPartTag;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID id() {
    return id;
  }

  public UUID uploadId() {
    return uploadId;
  }

  public int partNumber() {
    return partNumber;
  }

  public long size() {
    return size;
  }

  public String sha256() {
    return sha256;
  }

  public String providerPartTag() {
    return providerPartTag;
  }

  public UploadPartStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUploadId() {
    return uploadId;
  }

  public int getPartNumber() {
    return partNumber;
  }

  public long getSize() {
    return size;
  }

  public String getSha256() {
    return sha256;
  }

  public String getProviderPartTag() {
    return providerPartTag;
  }

  public UploadPartStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof UploadPart)) return false;
    UploadPart other = (UploadPart) value;
    return java.util.Objects.equals(id, other.id)
        && java.util.Objects.equals(uploadId, other.uploadId)
        && partNumber == other.partNumber
        && size == other.size
        && java.util.Objects.equals(sha256, other.sha256)
        && java.util.Objects.equals(providerPartTag, other.providerPartTag)
        && java.util.Objects.equals(status, other.status)
        && java.util.Objects.equals(createdAt, other.createdAt)
        && java.util.Objects.equals(updatedAt, other.updatedAt);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        id, uploadId, partNumber, size, sha256, providerPartTag, status, createdAt, updatedAt);
  }

  @Override
  public String toString() {
    return "UploadPart{"
        + "id="
        + id
        + ", uploadId="
        + uploadId
        + ", partNumber="
        + partNumber
        + ", size="
        + size
        + ", sha256="
        + sha256
        + ", providerPartTag="
        + providerPartTag
        + ", status="
        + status
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + "}";
  }
}
