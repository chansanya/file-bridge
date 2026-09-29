package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.service.ReconciliationService;
import io.github.chansan.filebridge.core.spi.*;
import java.time.*;
import java.util.UUID;

/** 数据库与物理存储基础对账服务。 */
public final class DefaultReconciliationService implements ReconciliationService {
  private static final int MAX_ATTEMPTS = 3;
  private static final Duration LEASE = Duration.ofMinutes(2);
  private final FileRepository files;
  private final ReconciliationRepository issues;
  private final StorageRegistry storages;
  private final FileBridgeMetrics metrics;
  private final String workerId;

  public DefaultReconciliationService(
      FileRepository files,
      ReconciliationRepository issues,
      StorageRegistry storages,
      FileBridgeMetrics metrics) {
    this.files = files;
    this.issues = issues;
    this.storages = storages;
    this.metrics = metrics;
    this.workerId = UUID.randomUUID().toString();
  }

  @Override
  public int reconcile(int limit) {
    Instant now = Instant.now();
    detectMissingObjects(now, limit);
    int processed = 0;
    for (ReconciliationIssue issue : issues.findDue(now, limit)) {
      if (!issues.acquire(issue.id(), workerId, now.plus(LEASE), now)) continue;
      process(issue);
      processed++;
    }
    metrics.record("reconciliation", "success", processed, 0);
    return processed;
  }

  private void detectMissingObjects(Instant now, int limit) {
    for (StorageObjectRecord object :
        files.findObjectsByStatus(StorageObjectStatus.AVAILABLE, limit)) {
      try {
        if (storages.require(object.location().storageId()).stat(object.location()).isEmpty()) {
          issues.upsert(
              fingerprint("PHYSICAL_OBJECT_MISSING", object.id()),
              "PHYSICAL_OBJECT_MISSING",
              object.location().storageId(),
              object.location().objectKey(),
              object.id().toString(),
              "Physical object is missing",
              now,
              now);
        }
      } catch (RuntimeException error) {
        issues.upsert(
            fingerprint("STORAGE_CHECK_FAILED", object.id()),
            "STORAGE_CHECK_FAILED",
            object.location().storageId(),
            object.location().objectKey(),
            object.id().toString(),
            message(error),
            now.plus(Duration.ofMinutes(1)),
            now);
      }
    }
  }

  private void process(ReconciliationIssue issue) {
    Instant now = Instant.now();
    try {
      UUID objectId = UUID.fromString(issue.entityId());
      StorageObjectRecord object = files.findObject(objectId).orElse(null);
      if (object == null) {
        issues.resolve(issue.id(), workerId, now);
        return;
      }
      switch (issue.issueType()) {
        case "PHYSICAL_OBJECT_MISSING" -> {
          files.markObjectError(objectId, "Physical object is missing", now);
          issues.requireManual(issue.id(), workerId, "Physical object is missing", now);
        }
        case "STORAGE_CHECK_FAILED" -> {
          if (storages.require(object.location().storageId()).stat(object.location()).isPresent()) {
            issues.resolve(issue.id(), workerId, now);
          } else {
            files.markObjectError(objectId, "Physical object is missing", now);
            issues.requireManual(issue.id(), workerId, "Physical object is missing", now);
          }
        }
        case "OBJECT_DELETE_FAILED" -> {
          storages.require(object.location().storageId()).delete(object.location());
          files.markObjectDeleted(objectId, now);
          issues.resolve(issue.id(), workerId, now);
        }
        default -> issues.requireManual(issue.id(), workerId, "Unsupported issue type", now);
      }
    } catch (RuntimeException error) {
      issues.retry(
          issue.id(), workerId, message(error), now.plus(Duration.ofMinutes(5)), MAX_ATTEMPTS, now);
    }
  }

  private static String fingerprint(String type, UUID objectId) {
    return type + ":" + objectId;
  }

  private static String message(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }
}
