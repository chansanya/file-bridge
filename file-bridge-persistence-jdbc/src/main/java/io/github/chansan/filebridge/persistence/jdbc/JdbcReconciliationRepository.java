package io.github.chansan.filebridge.persistence.jdbc;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.ReconciliationRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;

/** 对账问题的 JDBC 持久化实现。 */
public final class JdbcReconciliationRepository implements ReconciliationRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcReconciliationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ReconciliationIssue upsert(
      String fingerprint,
      String issueType,
      String storageId,
      String bucket,
      String objectKey,
      String providerUploadId,
      String entityId,
      String error,
      Instant nextAttemptAt,
      Instant now) {
    jdbc.update(
        "INSERT INTO fb_reconciliation_issue"
            + "(fingerprint,issue_type,storage_id,bucket_name,object_key,provider_upload_id,entity_id,"
            + "status,attempts,last_error,next_attempt_at,created_at,updated_at) "
            + "VALUES(:fingerprint,:type,:storage,:bucket,:objectKey,:providerUploadId,:entity,"
            + "'OPEN',0,:error,:next,:now,:now) "
            + "ON DUPLICATE KEY UPDATE "
            + "status=IF(fb_reconciliation_issue.status='RESOLVED','OPEN',fb_reconciliation_issue.status),"
            + "last_error=VALUES(last_error),next_attempt_at=VALUES(next_attempt_at),"
            + "resolved_at=NULL,updated_at=VALUES(updated_at)",
        params(
            fingerprint,
            issueType,
            storageId,
            bucket,
            objectKey,
            providerUploadId,
            entityId,
            error,
            nextAttemptAt,
            now));
    return jdbc
        .query(
            "SELECT * FROM fb_reconciliation_issue WHERE fingerprint=:fingerprint",
            Map.of("fingerprint", fingerprint),
            MAPPER)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  @Override
  public List<ReconciliationIssue> findDue(Instant now, int limit) {
    return jdbc.query(
        "SELECT * FROM fb_reconciliation_issue WHERE (status IN ('OPEN','RETRY_WAIT') OR (status='PROCESSING' AND lease_until<:now)) "
            + "AND (next_attempt_at IS NULL OR next_attempt_at<=:now) "
            + "AND (lease_owner IS NULL OR lease_until<:now) ORDER BY updated_at LIMIT "
            + safeLimit(limit),
        Map.of("now", ts(now)),
        MAPPER);
  }

  @Override
  public boolean acquire(long id, String owner, Instant until, Instant now) {
    return jdbc.update(
            "UPDATE fb_reconciliation_issue SET status='PROCESSING',lease_owner=:owner,"
                + "lease_until=:until,attempts=attempts+1,updated_at=:now WHERE id=:id "
                + "AND (status IN ('OPEN','RETRY_WAIT') OR (status='PROCESSING' AND lease_until<:now)) "
                + "AND (lease_owner IS NULL OR lease_until<:now)",
            Map.of("id", id, "owner", owner, "until", ts(until), "now", ts(now)))
        == 1;
  }

  @Override
  public void resolve(long id, String owner, Instant now) {
    updateTerminal(id, owner, "RESOLVED", null, now, true);
  }

  @Override
  public void retry(
      long id, String owner, String error, Instant next, int maxAttempts, Instant now) {
    jdbc.update(
        "UPDATE fb_reconciliation_issue SET "
            + "status=CASE WHEN attempts>=:max THEN 'MANUAL_REQUIRED' ELSE 'RETRY_WAIT' END,"
            + "last_error=:error,next_attempt_at=:next,lease_owner=NULL,lease_until=NULL,"
            + "updated_at=:now WHERE id=:id AND status='PROCESSING' AND lease_owner=:owner",
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("owner", owner)
            .addValue("max", maxAttempts)
            .addValue("error", abbreviate(error))
            .addValue("next", ts(next))
            .addValue("now", ts(now)));
  }

  @Override
  public void requireManual(long id, String owner, String error, Instant now) {
    updateTerminal(id, owner, "MANUAL_REQUIRED", error, now, false);
  }

  private void updateTerminal(
      long id, String owner, String status, String error, Instant now, boolean resolved) {
    jdbc.update(
        "UPDATE fb_reconciliation_issue SET status=:status,last_error=:error,"
            + "lease_owner=NULL,lease_until=NULL,resolved_at=:resolved,updated_at=:now "
            + "WHERE id=:id AND status='PROCESSING' AND lease_owner=:owner",
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("owner", owner)
            .addValue("status", status)
            .addValue("error", abbreviate(error))
            .addValue("resolved", resolved ? ts(now) : null)
            .addValue("now", ts(now)));
  }

  private static MapSqlParameterSource params(
      String fingerprint,
      String type,
      String storage,
      String bucket,
      String objectKey,
      String providerUploadId,
      String entity,
      String error,
      Instant next,
      Instant now) {
    return new MapSqlParameterSource()
        .addValue("fingerprint", fingerprint)
        .addValue("type", type)
        .addValue("storage", storage)
        .addValue("bucket", bucket)
        .addValue("objectKey", objectKey)
        .addValue("providerUploadId", providerUploadId)
        .addValue("entity", entity)
        .addValue("error", abbreviate(error))
        .addValue("next", ts(next))
        .addValue("now", ts(now));
  }

  private static String abbreviate(String value) {
    return value == null ? null : value.substring(0, Math.min(1000, value.length()));
  }

  private static int safeLimit(int value) {
    if (value < 1 || value > 1000) throw new IllegalArgumentException("limit must be 1..1000");
    return value;
  }

  private static Timestamp ts(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static final RowMapper<ReconciliationIssue> MAPPER =
      (row, index) ->
          new ReconciliationIssue(
              row.getLong("id"),
              row.getString("fingerprint"),
              row.getString("issue_type"),
              row.getString("storage_id"),
              row.getString("bucket_name"),
              row.getString("object_key"),
              row.getString("provider_upload_id"),
              row.getString("entity_id"),
              ReconciliationIssueStatus.valueOf(row.getString("status")),
              row.getInt("attempts"),
              row.getString("last_error"),
              instant(row, "next_attempt_at"),
              row.getString("lease_owner"),
              instant(row, "lease_until"),
              instant(row, "created_at"),
              instant(row, "updated_at"),
              instant(row, "resolved_at"));
}
