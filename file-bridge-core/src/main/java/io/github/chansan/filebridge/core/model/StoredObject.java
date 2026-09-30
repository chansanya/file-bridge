package io.github.chansan.filebridge.core.model;

import java.time.Instant;

/**
 * 存储适配器完成写入后返回的对象元数据。
 *
 * @param location 对象定位信息
 * @param size 实际写入字节数
 * @param sha256 写入过程中计算的摘要
 * @param contentType 内容类型
 * @param createdAt 写入时间
 */
public final class StoredObject {
  private final ObjectLocation location;
  private final long size;
  private final String sha256;
  private final String contentType;
  private final Instant createdAt;

  public StoredObject(
      ObjectLocation location, long size, String sha256, String contentType, Instant createdAt) {
    if (size < 0) throw new IllegalArgumentException("size must not be negative");

    this.location = location;
    this.size = size;
    this.sha256 = sha256;
    this.contentType = contentType;
    this.createdAt = createdAt;
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

  public Instant createdAt() {
    return createdAt;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof StoredObject)) return false;
    StoredObject other = (StoredObject) value;
    return java.util.Objects.equals(location, other.location)
        && size == other.size
        && java.util.Objects.equals(sha256, other.sha256)
        && java.util.Objects.equals(contentType, other.contentType)
        && java.util.Objects.equals(createdAt, other.createdAt);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(location, size, sha256, contentType, createdAt);
  }

  @Override
  public String toString() {
    return "StoredObject{"
        + "location="
        + location
        + ", size="
        + size
        + ", sha256="
        + sha256
        + ", contentType="
        + contentType
        + ", createdAt="
        + createdAt
        + "}";
  }
}
