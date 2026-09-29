package io.github.chansan.filebridge.core.model;

import java.time.Instant;

/** 持久化的数据库与物理存储对账问题。 */
public record ReconciliationIssue(
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
    Instant resolvedAt) {}
