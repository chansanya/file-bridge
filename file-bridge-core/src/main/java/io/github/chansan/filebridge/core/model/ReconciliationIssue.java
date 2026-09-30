package io.github.chansan.filebridge.core.model;

import java.time.Instant;

/** 持久化的数据库与物理存储对账问题。 */
public final class ReconciliationIssue {
  private final long id;
  private final String fingerprint;
  private final String issueType;
  private final String storageId;
  private final String bucket;
  private final String objectKey;
  private final String providerUploadId;
  private final String entityId;
  private final ReconciliationIssueStatus status;
  private final int attempts;
  private final String lastError;
  private final Instant nextAttemptAt;
  private final String leaseOwner;
  private final Instant leaseUntil;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant resolvedAt;

  public ReconciliationIssue(
      long id,
      String fingerprint,
      String issueType,
      String storageId,
      String bucket,
      String objectKey,
      String providerUploadId,
      String entityId,
      ReconciliationIssueStatus status,
      int attempts,
      String lastError,
      Instant nextAttemptAt,
      String leaseOwner,
      Instant leaseUntil,
      Instant createdAt,
      Instant updatedAt,
      Instant resolvedAt) {
    this.id = id;
    this.fingerprint = fingerprint;
    this.issueType = issueType;
    this.storageId = storageId;
    this.bucket = bucket;
    this.objectKey = objectKey;
    this.providerUploadId = providerUploadId;
    this.entityId = entityId;
    this.status = status;
    this.attempts = attempts;
    this.lastError = lastError;
    this.nextAttemptAt = nextAttemptAt;
    this.leaseOwner = leaseOwner;
    this.leaseUntil = leaseUntil;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.resolvedAt = resolvedAt;
  }

  public long id() {
    return id;
  }

  public String fingerprint() {
    return fingerprint;
  }

  public String issueType() {
    return issueType;
  }

  public String storageId() {
    return storageId;
  }

  public String bucket() {
    return bucket;
  }

  public String objectKey() {
    return objectKey;
  }

  public String providerUploadId() {
    return providerUploadId;
  }

  public String entityId() {
    return entityId;
  }

  public ReconciliationIssueStatus status() {
    return status;
  }

  public int attempts() {
    return attempts;
  }

  public String lastError() {
    return lastError;
  }

  public Instant nextAttemptAt() {
    return nextAttemptAt;
  }

  public String leaseOwner() {
    return leaseOwner;
  }

  public Instant leaseUntil() {
    return leaseUntil;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Instant resolvedAt() {
    return resolvedAt;
  }

  public long getId() {
    return id;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getIssueType() {
    return issueType;
  }

  public String getStorageId() {
    return storageId;
  }

  public String getBucket() {
    return bucket;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getProviderUploadId() {
    return providerUploadId;
  }

  public String getEntityId() {
    return entityId;
  }

  public ReconciliationIssueStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLeaseOwner() {
    return leaseOwner;
  }

  public Instant getLeaseUntil() {
    return leaseUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof ReconciliationIssue)) return false;
    ReconciliationIssue other = (ReconciliationIssue) value;
    return id == other.id
        && java.util.Objects.equals(fingerprint, other.fingerprint)
        && java.util.Objects.equals(issueType, other.issueType)
        && java.util.Objects.equals(storageId, other.storageId)
        && java.util.Objects.equals(bucket, other.bucket)
        && java.util.Objects.equals(objectKey, other.objectKey)
        && java.util.Objects.equals(providerUploadId, other.providerUploadId)
        && java.util.Objects.equals(entityId, other.entityId)
        && java.util.Objects.equals(status, other.status)
        && attempts == other.attempts
        && java.util.Objects.equals(lastError, other.lastError)
        && java.util.Objects.equals(nextAttemptAt, other.nextAttemptAt)
        && java.util.Objects.equals(leaseOwner, other.leaseOwner)
        && java.util.Objects.equals(leaseUntil, other.leaseUntil)
        && java.util.Objects.equals(createdAt, other.createdAt)
        && java.util.Objects.equals(updatedAt, other.updatedAt)
        && java.util.Objects.equals(resolvedAt, other.resolvedAt);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        id,
        fingerprint,
        issueType,
        storageId,
        bucket,
        objectKey,
        providerUploadId,
        entityId,
        status,
        attempts,
        lastError,
        nextAttemptAt,
        leaseOwner,
        leaseUntil,
        createdAt,
        updatedAt,
        resolvedAt);
  }

  @Override
  public String toString() {
    return "ReconciliationIssue{"
        + "id="
        + id
        + ", fingerprint="
        + fingerprint
        + ", issueType="
        + issueType
        + ", storageId="
        + storageId
        + ", bucket="
        + bucket
        + ", objectKey="
        + objectKey
        + ", providerUploadId="
        + providerUploadId
        + ", entityId="
        + entityId
        + ", status="
        + status
        + ", attempts="
        + attempts
        + ", lastError="
        + lastError
        + ", nextAttemptAt="
        + nextAttemptAt
        + ", leaseOwner="
        + leaseOwner
        + ", leaseUntil="
        + leaseUntil
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + ", resolvedAt="
        + resolvedAt
        + "}";
  }
}
