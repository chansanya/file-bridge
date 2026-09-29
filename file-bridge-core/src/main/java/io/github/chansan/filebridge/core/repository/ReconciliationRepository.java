package io.github.chansan.filebridge.core.repository;

import io.github.chansan.filebridge.core.model.ReconciliationIssue;
import java.time.Instant;
import java.util.List;

/** 对账问题、重试退避和处理租约的持久化端口。 */
public interface ReconciliationRepository {
  ReconciliationIssue upsert(
      String fingerprint,
      String issueType,
      String storageId,
      String objectKey,
      String entityId,
      String error,
      Instant nextAttemptAt,
      Instant now);

  List<ReconciliationIssue> findDue(Instant now, int limit);

  boolean acquire(long id, String owner, Instant leaseUntil, Instant now);

  void resolve(long id, String owner, Instant now);

  void retry(
      long id, String owner, String error, Instant nextAttemptAt, int maxAttempts, Instant now);

  void requireManual(long id, String owner, String error, Instant now);
}
