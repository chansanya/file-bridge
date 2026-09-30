package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 持久化的分片上传任务。
 *
 * @param id 任务 ID
 * @param tenantId 租户 ID
 * @param ownerId 用户 ID
 * @param storageId 目标存储实例 ID
 * @param objectKey 目标对象路径
 * @param originalName 原始文件名
 * @param businessType 业务类型
 * @param businessId 业务标识
 * @param contentType 内容类型
 * @param expectedSize 预期总字节数
 * @param claimedSha256 客户端声明的完整摘要
 * @param partSize 分片字节数
 * @param totalParts 分片总数
 * @param status 任务状态
 * @param providerUploadId 平台上传 ID
 * @param resultFileId 完成后业务文件 ID
 * @param expiresAt 任务过期时间
 * @param leaseOwner 完成租约持有者
 * @param leaseUntil 租约截止时间
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public final class UploadTask {
  private final UUID id;
  private final String tenantId;
  private final String ownerId;
  private final String storageId;
  private final String objectKey;
  private final String originalName;
  private final String businessType;
  private final String businessId;
  private final String contentType;
  private final long expectedSize;
  private final String claimedSha256;
  private final long partSize;
  private final int totalParts;
  private final UploadTaskStatus status;
  private final String providerUploadId;
  private final UUID resultFileId;
  private final Instant expiresAt;
  private final String leaseOwner;
  private final Instant leaseUntil;
  private final long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  public UploadTask(
      UUID id,
      String tenantId,
      String ownerId,
      String storageId,
      String objectKey,
      String originalName,
      String businessType,
      String businessId,
      String contentType,
      long expectedSize,
      String claimedSha256,
      long partSize,
      int totalParts,
      UploadTaskStatus status,
      String providerUploadId,
      UUID resultFileId,
      Instant expiresAt,
      String leaseOwner,
      Instant leaseUntil,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.storageId = storageId;
    this.objectKey = objectKey;
    this.originalName = originalName;
    this.businessType = businessType;
    this.businessId = businessId;
    this.contentType = contentType;
    this.expectedSize = expectedSize;
    this.claimedSha256 = claimedSha256;
    this.partSize = partSize;
    this.totalParts = totalParts;
    this.status = status;
    this.providerUploadId = providerUploadId;
    this.resultFileId = resultFileId;
    this.expiresAt = expiresAt;
    this.leaseOwner = leaseOwner;
    this.leaseUntil = leaseUntil;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID id() {
    return id;
  }

  public String tenantId() {
    return tenantId;
  }

  public String ownerId() {
    return ownerId;
  }

  public String storageId() {
    return storageId;
  }

  public String objectKey() {
    return objectKey;
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

  public String contentType() {
    return contentType;
  }

  public long expectedSize() {
    return expectedSize;
  }

  public String claimedSha256() {
    return claimedSha256;
  }

  public long partSize() {
    return partSize;
  }

  public int totalParts() {
    return totalParts;
  }

  public UploadTaskStatus status() {
    return status;
  }

  public String providerUploadId() {
    return providerUploadId;
  }

  public UUID resultFileId() {
    return resultFileId;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public String leaseOwner() {
    return leaseOwner;
  }

  public Instant leaseUntil() {
    return leaseUntil;
  }

  public long version() {
    return version;
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

  public String getTenantId() {
    return tenantId;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getStorageId() {
    return storageId;
  }

  public String getObjectKey() {
    return objectKey;
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

  public String getContentType() {
    return contentType;
  }

  public long getExpectedSize() {
    return expectedSize;
  }

  public String getClaimedSha256() {
    return claimedSha256;
  }

  public long getPartSize() {
    return partSize;
  }

  public int getTotalParts() {
    return totalParts;
  }

  public UploadTaskStatus getStatus() {
    return status;
  }

  public String getProviderUploadId() {
    return providerUploadId;
  }

  public UUID getResultFileId() {
    return resultFileId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public String getLeaseOwner() {
    return leaseOwner;
  }

  public Instant getLeaseUntil() {
    return leaseUntil;
  }

  public long getVersion() {
    return version;
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
    if (!(value instanceof UploadTask)) return false;
    UploadTask other = (UploadTask) value;
    return java.util.Objects.equals(id, other.id)
        && java.util.Objects.equals(tenantId, other.tenantId)
        && java.util.Objects.equals(ownerId, other.ownerId)
        && java.util.Objects.equals(storageId, other.storageId)
        && java.util.Objects.equals(objectKey, other.objectKey)
        && java.util.Objects.equals(originalName, other.originalName)
        && java.util.Objects.equals(businessType, other.businessType)
        && java.util.Objects.equals(businessId, other.businessId)
        && java.util.Objects.equals(contentType, other.contentType)
        && expectedSize == other.expectedSize
        && java.util.Objects.equals(claimedSha256, other.claimedSha256)
        && partSize == other.partSize
        && totalParts == other.totalParts
        && java.util.Objects.equals(status, other.status)
        && java.util.Objects.equals(providerUploadId, other.providerUploadId)
        && java.util.Objects.equals(resultFileId, other.resultFileId)
        && java.util.Objects.equals(expiresAt, other.expiresAt)
        && java.util.Objects.equals(leaseOwner, other.leaseOwner)
        && java.util.Objects.equals(leaseUntil, other.leaseUntil)
        && version == other.version
        && java.util.Objects.equals(createdAt, other.createdAt)
        && java.util.Objects.equals(updatedAt, other.updatedAt);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        id,
        tenantId,
        ownerId,
        storageId,
        objectKey,
        originalName,
        businessType,
        businessId,
        contentType,
        expectedSize,
        claimedSha256,
        partSize,
        totalParts,
        status,
        providerUploadId,
        resultFileId,
        expiresAt,
        leaseOwner,
        leaseUntil,
        version,
        createdAt,
        updatedAt);
  }

  @Override
  public String toString() {
    return "UploadTask{"
        + "id="
        + id
        + ", tenantId="
        + tenantId
        + ", ownerId="
        + ownerId
        + ", storageId="
        + storageId
        + ", objectKey="
        + objectKey
        + ", originalName="
        + originalName
        + ", businessType="
        + businessType
        + ", businessId="
        + businessId
        + ", contentType="
        + contentType
        + ", expectedSize="
        + expectedSize
        + ", claimedSha256="
        + claimedSha256
        + ", partSize="
        + partSize
        + ", totalParts="
        + totalParts
        + ", status="
        + status
        + ", providerUploadId="
        + providerUploadId
        + ", resultFileId="
        + resultFileId
        + ", expiresAt="
        + expiresAt
        + ", leaseOwner="
        + leaseOwner
        + ", leaseUntil="
        + leaseUntil
        + ", version="
        + version
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + "}";
  }
}
