package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 持久化的物理对象元数据。
 *
 * @param id 物理对象 ID
 * @param location 存储定位信息
 * @param size 对象字节数
 * @param sha256 服务端校验摘要
 * @param contentType 内容类型
 * @param status 对象状态
 * @param verifiedAt 可信校验完成时间
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param version 乐观锁版本
 */
public final class StorageObjectRecord {
  private final UUID id;
  private final ObjectLocation location;
  private final long size;
  private final String sha256;
  private final String contentType;
  private final StorageObjectStatus status;
  private final Instant verifiedAt;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final long version;

  public StorageObjectRecord(
      UUID id,
      ObjectLocation location,
      long size,
      String sha256,
      String contentType,
      StorageObjectStatus status,
      Instant verifiedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = id;
    this.location = location;
    this.size = size;
    this.sha256 = sha256;
    this.contentType = contentType;
    this.status = status;
    this.verifiedAt = verifiedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  public UUID id() {
    return id;
  }

  public ObjectLocation location() {
    return location;
  }

  public long size() {
    return size;
  }

  public String sha256() {
    return sha256;
  }

  public String contentType() {
    return contentType;
  }

  public StorageObjectStatus status() {
    return status;
  }

  public Instant verifiedAt() {
    return verifiedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }

  public UUID getId() {
    return id;
  }

  public ObjectLocation getLocation() {
    return location;
  }

  public long getSize() {
    return size;
  }

  public String getSha256() {
    return sha256;
  }

  public String getContentType() {
    return contentType;
  }

  public StorageObjectStatus getStatus() {
    return status;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof StorageObjectRecord)) return false;
    StorageObjectRecord other = (StorageObjectRecord) value;
    return java.util.Objects.equals(id, other.id)
        && java.util.Objects.equals(location, other.location)
        && size == other.size
        && java.util.Objects.equals(sha256, other.sha256)
        && java.util.Objects.equals(contentType, other.contentType)
        && java.util.Objects.equals(status, other.status)
        && java.util.Objects.equals(verifiedAt, other.verifiedAt)
        && java.util.Objects.equals(createdAt, other.createdAt)
        && java.util.Objects.equals(updatedAt, other.updatedAt)
        && version == other.version;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        id, location, size, sha256, contentType, status, verifiedAt, createdAt, updatedAt, version);
  }

  @Override
  public String toString() {
    return "StorageObjectRecord{"
        + "id="
        + id
        + ", location="
        + location
        + ", size="
        + size
        + ", sha256="
        + sha256
        + ", contentType="
        + contentType
        + ", status="
        + status
        + ", verifiedAt="
        + verifiedAt
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + ", version="
        + version
        + "}";
  }
}
