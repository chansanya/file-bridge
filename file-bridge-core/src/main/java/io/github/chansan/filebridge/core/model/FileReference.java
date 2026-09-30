package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 面向业务的文件引用。
 *
 * @param id 业务文件 ID
 * @param objectId 关联物理对象 ID
 * @param tenantId 租户 ID
 * @param ownerId 所有者 ID
 * @param originalName 原始文件名
 * @param businessType 业务类型
 * @param businessId 业务标识
 * @param status 引用状态
 * @param createdAt 创建时间
 * @param deletedAt 逻辑删除时间
 */
public final class FileReference {
  private final UUID id;
  private final UUID objectId;
  private final String tenantId;
  private final String ownerId;
  private final String originalName;
  private final String businessType;
  private final String businessId;
  private final FileReferenceStatus status;
  private final Instant createdAt;
  private final Instant deletedAt;

  public FileReference(
      UUID id,
      UUID objectId,
      String tenantId,
      String ownerId,
      String originalName,
      String businessType,
      String businessId,
      FileReferenceStatus status,
      Instant createdAt,
      Instant deletedAt) {
    this.id = id;
    this.objectId = objectId;
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.originalName = originalName;
    this.businessType = businessType;
    this.businessId = businessId;
    this.status = status;
    this.createdAt = createdAt;
    this.deletedAt = deletedAt;
  }

  public UUID id() {
    return id;
  }

  public UUID objectId() {
    return objectId;
  }

  public String tenantId() {
    return tenantId;
  }

  public String ownerId() {
    return ownerId;
  }

  public String originalName() {
    return originalName;
  }

  public String businessType() {
    return businessType;
  }

  public String businessId() {
    return businessId;
  }

  public FileReferenceStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant deletedAt() {
    return deletedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getObjectId() {
    return objectId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getOriginalName() {
    return originalName;
  }

  public String getBusinessType() {
    return businessType;
  }

  public String getBusinessId() {
    return businessId;
  }

  public FileReferenceStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof FileReference)) return false;
    FileReference other = (FileReference) value;
    return java.util.Objects.equals(id, other.id)
        && java.util.Objects.equals(objectId, other.objectId)
        && java.util.Objects.equals(tenantId, other.tenantId)
        && java.util.Objects.equals(ownerId, other.ownerId)
        && java.util.Objects.equals(originalName, other.originalName)
        && java.util.Objects.equals(businessType, other.businessType)
        && java.util.Objects.equals(businessId, other.businessId)
        && java.util.Objects.equals(status, other.status)
        && java.util.Objects.equals(createdAt, other.createdAt)
        && java.util.Objects.equals(deletedAt, other.deletedAt);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        id,
        objectId,
        tenantId,
        ownerId,
        originalName,
        businessType,
        businessId,
        status,
        createdAt,
        deletedAt);
  }

  @Override
  public String toString() {
    return "FileReference{"
        + "id="
        + id
        + ", objectId="
        + objectId
        + ", tenantId="
        + tenantId
        + ", ownerId="
        + ownerId
        + ", originalName="
        + originalName
        + ", businessType="
        + businessType
        + ", businessId="
        + businessId
        + ", status="
        + status
        + ", createdAt="
        + createdAt
        + ", deletedAt="
        + deletedAt
        + "}";
  }
}
