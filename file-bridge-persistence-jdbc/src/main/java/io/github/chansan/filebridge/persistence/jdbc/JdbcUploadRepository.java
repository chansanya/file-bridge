package io.github.chansan.filebridge.persistence.jdbc;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.UploadRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;

/** 上传任务和分片的 JDBC 仓储实现。 */
public final class JdbcUploadRepository implements UploadRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcUploadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UploadTask insertTask(UploadTask t) {
    jdbc.update(
        "INSERT INTO"
            + " fb_upload_task(id,tenant_id,owner_id,storage_id,object_key,original_name,business_type,business_id,content_type,expected_size,claimed_sha256,part_size,total_parts,status,provider_upload_id,result_file_id,expires_at,lease_owner,lease_until,version,created_at,updated_at)"
            + " VALUES(:id,:tenant,:owner,:storage,:key,:name,:bt,:bid,:ct,:size,:sha,:ps,:total,:status,:provider,:result,:expires,:leaseOwner,:leaseUntil,:version,:created,:updated)",
        params(t));
    return t;
  }

  @Override
  public Optional<UploadTask> findTask(UUID id) {
    return jdbc
        .query("SELECT * FROM fb_upload_task WHERE id=:id", Map.of("id", id.toString()), TASK)
        .stream()
        .findFirst();
  }

  @Override
  public List<UploadPart> findCompletedParts(UUID id) {
    return jdbc.query(
        "SELECT * FROM fb_upload_part WHERE upload_id=:id AND status='COMPLETED' ORDER BY"
            + " part_number",
        Map.of("id", id.toString()),
        PART);
  }

  @Override
  public UploadPart saveCompletedPart(UploadPart p) {
    try {
      jdbc.update(
          "INSERT INTO"
              + " fb_upload_part(id,upload_id,part_number,size_bytes,sha256,provider_part_tag,status,created_at,updated_at)"
              + " VALUES(:id,:upload,:part,:size,:sha,:tag,:status,:created,:updated)",
          new MapSqlParameterSource()
              .addValue("id", p.id().toString())
              .addValue("upload", p.uploadId().toString())
              .addValue("part", p.partNumber())
              .addValue("size", p.size())
              .addValue("sha", p.sha256())
              .addValue("tag", p.providerPartTag())
              .addValue("status", p.status().name())
              .addValue("created", ts(p.createdAt()))
              .addValue("updated", ts(p.updatedAt())));
      return p;
    } catch (DuplicateKeyException e) {
      // 唯一约束负责并发裁决：相同内容视为幂等，不同内容返回冲突。
      UploadPart old =
          jdbc
              .query(
                  "SELECT * FROM fb_upload_part WHERE upload_id=:u AND part_number=:p",
                  Map.of("u", p.uploadId().toString(), "p", p.partNumber()),
                  PART)
              .stream()
              .findFirst()
              .orElseThrow();
      if (old.size() == p.size() && old.sha256().equals(p.sha256())) return old;
      throw new FileBridgeException(
          FileBridgeErrorCode.UPLOAD_PART_CONFLICT,
          "Part already exists with different content",
          e);
    }
  }

  @Override
  public boolean markUploading(UUID id, long version, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET status='UPLOADING',version=version+1,updated_at=:now WHERE"
                + " id=:id AND status='CREATED' AND version=:version",
            Map.of("id", id.toString(), "version", version, "now", ts(now)))
        == 1;
  }

  // 条件更新同时校验状态和租约时间，避免多个实例并发完成同一任务。
  @Override
  public boolean acquireCompletionLease(UUID id, String owner, Instant until, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET"
                + " status='COMPLETING',lease_owner=:owner,lease_until=:until,version=version+1,updated_at=:now"
                + " WHERE id=:id AND (status IN ('CREATED','UPLOADING') OR (status IN"
                + " ('COMPLETING','VERIFYING') AND lease_until<:now))",
            Map.of("id", id.toString(), "owner", owner, "until", ts(until), "now", ts(now)))
        == 1;
  }

  @Override
  public boolean moveToVerifying(UUID id, String owner, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET status='VERIFYING',version=version+1,updated_at=:now WHERE"
                + " id=:id AND status='COMPLETING' AND lease_owner=:owner",
            Map.of("id", id.toString(), "owner", owner, "now", ts(now)))
        == 1;
  }

  @Override
  public boolean complete(UUID id, String owner, UUID fileId, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET"
                + " status='COMPLETED',result_file_id=:file,lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=:now"
                + " WHERE id=:id AND status='VERIFYING' AND lease_owner=:owner",
            Map.of("id", id.toString(), "owner", owner, "file", fileId.toString(), "now", ts(now)))
        == 1;
  }

  @Override
  public boolean cancel(UUID id, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET"
                + " status='CANCELLED',lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=:now"
                + " WHERE id=:id AND status IN ('CREATED','UPLOADING')",
            Map.of("id", id.toString(), "now", ts(now)))
        == 1;
  }

  @Override
  public void fail(UUID id, String owner, Instant now) {
    jdbc.update(
        "UPDATE fb_upload_task SET"
            + " status='FAILED',lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=:now"
            + " WHERE id=:id AND lease_owner=:owner AND status IN ('COMPLETING','VERIFYING')",
        Map.of("id", id.toString(), "owner", owner, "now", ts(now)));
  }

  @Override
  public List<UploadTask> findCompletableOrExpiredLeases(Instant now, int limit) {
    return jdbc.query(
        "SELECT * FROM fb_upload_task WHERE status='COMPLETING' OR (status='VERIFYING' AND"
            + " lease_until<:now) ORDER BY updated_at LIMIT "
            + safeLimit(limit),
        Map.of("now", ts(now)),
        TASK);
  }

  @Override
  public List<UploadTask> findExpiredTasks(Instant now, int limit) {
    return jdbc.query(
        "SELECT * FROM fb_upload_task WHERE status IN ('CREATED','UPLOADING') AND expires_at<:now"
            + " ORDER BY expires_at LIMIT "
            + safeLimit(limit),
        Map.of("now", ts(now)),
        TASK);
  }

  private static int safeLimit(int v) {
    if (v < 1 || v > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
    return v;
  }

  private static MapSqlParameterSource params(UploadTask t) {
    return new MapSqlParameterSource()
        .addValue("id", s(t.id()))
        .addValue("tenant", t.tenantId())
        .addValue("owner", t.ownerId())
        .addValue("storage", t.storageId())
        .addValue("key", t.objectKey())
        .addValue("name", t.originalName())
        .addValue("bt", t.businessType())
        .addValue("bid", t.businessId())
        .addValue("ct", t.contentType())
        .addValue("size", t.expectedSize())
        .addValue("sha", t.claimedSha256())
        .addValue("ps", t.partSize())
        .addValue("total", t.totalParts())
        .addValue("status", t.status().name())
        .addValue("provider", t.providerUploadId())
        .addValue("result", s(t.resultFileId()))
        .addValue("expires", ts(t.expiresAt()))
        .addValue("leaseOwner", t.leaseOwner())
        .addValue("leaseUntil", ts(t.leaseUntil()))
        .addValue("version", t.version())
        .addValue("created", ts(t.createdAt()))
        .addValue("updated", ts(t.updatedAt()));
  }

  private static final RowMapper<UploadTask> TASK =
      (r, n) ->
          new UploadTask(
              uuid(r, "id"),
              r.getString("tenant_id"),
              r.getString("owner_id"),
              r.getString("storage_id"),
              r.getString("object_key"),
              r.getString("original_name"),
              r.getString("business_type"),
              r.getString("business_id"),
              r.getString("content_type"),
              r.getLong("expected_size"),
              r.getString("claimed_sha256"),
              r.getLong("part_size"),
              r.getInt("total_parts"),
              UploadTaskStatus.valueOf(r.getString("status")),
              r.getString("provider_upload_id"),
              uuid(r, "result_file_id"),
              instant(r, "expires_at"),
              r.getString("lease_owner"),
              instant(r, "lease_until"),
              r.getLong("version"),
              instant(r, "created_at"),
              instant(r, "updated_at"));
  private static final RowMapper<UploadPart> PART =
      (r, n) ->
          new UploadPart(
              uuid(r, "id"),
              uuid(r, "upload_id"),
              r.getInt("part_number"),
              r.getLong("size_bytes"),
              r.getString("sha256"),
              r.getString("provider_part_tag"),
              UploadPartStatus.valueOf(r.getString("status")),
              instant(r, "created_at"),
              instant(r, "updated_at"));

  private static String s(UUID v) {
    return v == null ? null : v.toString();
  }

  private static Timestamp ts(Instant v) {
    return v == null ? null : Timestamp.from(v);
  }

  private static UUID uuid(ResultSet r, String c) throws SQLException {
    String v = r.getString(c);
    return v == null ? null : UUID.fromString(v);
  }

  private static Instant instant(ResultSet r, String c) throws SQLException {
    Timestamp t = r.getTimestamp(c);
    return t == null ? null : t.toInstant();
  }
}
