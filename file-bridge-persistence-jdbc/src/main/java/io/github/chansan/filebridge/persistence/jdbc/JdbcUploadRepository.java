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

  /**
   * 创建上传任务仓储。
   *
   * @param jdbc 具名参数 JDBC 模板
   */
  public JdbcUploadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * {@inheritDoc}
   *
   * @param t 待保存任务
   * @return 已保存任务
   */
  @Override
  public UploadTask insertTask(UploadTask t) {
    jdbc.update(
        "INSERT INTO"
            + " fb_upload_task(id,tenant_id,owner_id,storage_id,object_key,original_name,business_type,business_id,content_type,expected_size,claimed_sha256,part_size,total_parts,status,provider_upload_id,result_file_id,expires_at,lease_owner,lease_until,version,created_at,updated_at)"
            + " VALUES(:id,:tenant,:owner,:storage,:key,:name,:bt,:bid,:ct,:size,:sha,:ps,:total,:status,:provider,:result,:expires,:leaseOwner,:leaseUntil,:version,:created,:updated)",
        params(t));
    return t;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @return 上传任务，不存在时返回空
   */
  @Override
  public Optional<UploadTask> findTask(UUID id) {
    return jdbc
        .query("SELECT * FROM fb_upload_task WHERE id=:id", Map.of("id", id.toString()), TASK)
        .stream()
        .findFirst();
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @return 已确认成功的分片，按序号升序排列
   */
  @Override
  public List<UploadPart> findCompletedParts(UUID id) {
    return jdbc.query(
        "SELECT * FROM fb_upload_part WHERE upload_id=:id AND status='COMPLETED' ORDER BY"
            + " part_number",
        Map.of("id", id.toString()),
        PART);
  }

  /**
   * {@inheritDoc}
   *
   * @param p 已确认的分片
   * @return 保存后的分片记录
   */
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

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @param version 预期乐观锁版本
   * @param now 当前时间
   * @return 条件匹配并成功更新时返回 {@code true}
   */
  @Override
  public boolean markUploading(UUID id, long version, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET status='UPLOADING',version=version+1,updated_at=:now WHERE"
                + " id=:id AND status='CREATED' AND version=:version",
            Map.of("id", id.toString(), "version", version, "now", ts(now)))
        == 1;
  }

  /**
   * {@inheritDoc}
   *
   * <p>条件更新同时校验状态和租约时间，避免多个实例并发完成同一任务。
   *
   * @param id 上传任务 ID
   * @param owner 租约持有者
   * @param until 租约截止时间
   * @param now 当前时间
   * @return 当前执行者成功持有租约时返回 {@code true}
   */
  @Override
  public boolean requestCompletion(UUID id, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET status='COMPLETING',lease_owner=NULL,lease_until=NULL,"
                + "next_attempt_at=:now,last_error=NULL,version=version+1,updated_at=:now "
                + "WHERE id=:id AND status IN ('CREATED','UPLOADING')",
            Map.of("id", id.toString(), "now", ts(now)))
        == 1;
  }

  @Override
  public boolean acquireCompletionLease(UUID id, String owner, Instant until, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET lease_owner=:owner,lease_until=:until,"
                + "completion_attempts=completion_attempts+1,version=version+1,updated_at=:now "
                + "WHERE id=:id AND status IN ('COMPLETING','VERIFYING') "
                + "AND (lease_owner IS NULL OR lease_until<:now) "
                + "AND (next_attempt_at IS NULL OR next_attempt_at<=:now)",
            Map.of("id", id.toString(), "owner", owner, "until", ts(until), "now", ts(now)))
        == 1;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @param owner 租约持有者
   * @param now 当前时间
   * @return 状态和租约匹配时返回 {@code true}
   */
  @Override
  public boolean renewCompletionLease(UUID id, String owner, Instant until, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET lease_until=:until,updated_at=:now "
                + "WHERE id=:id AND lease_owner=:owner AND status IN ('COMPLETING','VERIFYING')",
            Map.of("id", id.toString(), "owner", owner, "until", ts(until), "now", ts(now)))
        == 1;
  }

  @Override
  public void retryCompletion(
      UUID id, String owner, String error, Instant nextAttemptAt, int maxAttempts, Instant now) {
    jdbc.update(
        "UPDATE fb_upload_task SET "
            + "status=CASE WHEN completion_attempts>=:maxAttempts THEN 'FAILED' ELSE 'COMPLETING' END,"
            + "lease_owner=NULL,lease_until=NULL,next_attempt_at=:nextAttempt,last_error=:error,"
            + "version=version+1,updated_at=:now "
            + "WHERE id=:id AND lease_owner=:owner AND status IN ('COMPLETING','VERIFYING')",
        new MapSqlParameterSource()
            .addValue("id", id.toString())
            .addValue("owner", owner)
            .addValue("error", abbreviate(error))
            .addValue("nextAttempt", ts(nextAttemptAt))
            .addValue("maxAttempts", maxAttempts)
            .addValue("now", ts(now)));
  }

  @Override
  public boolean moveToVerifying(UUID id, String owner, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET status='VERIFYING',version=version+1,updated_at=:now WHERE"
                + " id=:id AND status='COMPLETING' AND lease_owner=:owner",
            Map.of("id", id.toString(), "owner", owner, "now", ts(now)))
        == 1;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @param owner 租约持有者
   * @param fileId 完成后业务文件 ID
   * @param now 当前时间
   * @return 成功提交结果时返回 {@code true}
   */
  @Override
  public boolean complete(UUID id, String owner, UUID fileId, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET"
                + " status='COMPLETED',result_file_id=:file,lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=:now"
                + " WHERE id=:id AND status='VERIFYING' AND lease_owner=:owner",
            Map.of("id", id.toString(), "owner", owner, "file", fileId.toString(), "now", ts(now)))
        == 1;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @param now 当前时间
   * @return 成功取消时返回 {@code true}
   */
  @Override
  public boolean cancel(UUID id, Instant now) {
    return jdbc.update(
            "UPDATE fb_upload_task SET"
                + " status='CANCELLED',lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=:now"
                + " WHERE id=:id AND status IN ('CREATED','UPLOADING')",
            Map.of("id", id.toString(), "now", ts(now)))
        == 1;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @param owner 租约持有者
   * @param now 当前时间
   */
  @Override
  public void fail(UUID id, String owner, String error, Instant now) {
    jdbc.update(
        "UPDATE fb_upload_task SET status='FAILED',lease_owner=NULL,lease_until=NULL,"
            + "last_error=:error,version=version+1,updated_at=:now "
            + "WHERE id=:id AND lease_owner=:owner AND status IN ('COMPLETING','VERIFYING')",
        new MapSqlParameterSource()
            .addValue("id", id.toString())
            .addValue("owner", owner)
            .addValue("error", abbreviate(error))
            .addValue("now", ts(now)));
  }

  /**
   * {@inheritDoc}
   *
   * @param now 当前时间
   * @param limit 最大返回数量
   * @return 可由工作器竞争的任务
   */
  @Override
  public List<UploadTask> findCompletableOrExpiredLeases(Instant now, int limit) {
    return jdbc.query(
        "SELECT * FROM fb_upload_task WHERE status IN ('COMPLETING','VERIFYING') "
            + "AND (lease_owner IS NULL OR lease_until<:now) "
            + "AND (next_attempt_at IS NULL OR next_attempt_at<=:now) ORDER BY updated_at LIMIT "
            + safeLimit(limit),
        Map.of("now", ts(now)),
        TASK);
  }

  /**
   * {@inheritDoc}
   *
   * @param now 当前时间
   * @param limit 最大返回数量
   * @return 待清理任务
   */
  @Override
  public List<UploadTask> findExpiredTasks(Instant now, int limit) {
    return jdbc.query(
        "SELECT * FROM fb_upload_task WHERE status IN ('CREATED','UPLOADING') AND expires_at<:now"
            + " ORDER BY expires_at LIMIT "
            + safeLimit(limit),
        Map.of("now", ts(now)),
        TASK);
  }

  /**
   * 校验并返回分页上限。
   *
   * @param v 调用方传入的数量上限
   * @return 合法数量上限
   */
  private static String abbreviate(String value) {
    if (value == null) return null;
    return value.substring(0, Math.min(1000, value.length()));
  }

  private static int safeLimit(int v) {
    if (v < 1 || v > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
    return v;
  }

  /**
   * 构建任务持久化参数。
   *
   * @param t 待持久化任务
   * @return SQL 具名参数
   */
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

  /**
   * 转换可空 UUID 为字符串。
   *
   * @param v UUID 值
   * @return UUID 字符串，空值时返回 {@code null}
   */
  private static String s(UUID v) {
    return v == null ? null : v.toString();
  }

  /**
   * 转换可空时间戳。
   *
   * @param v 时间值
   * @return JDBC 时间戳，空值时返回 {@code null}
   */
  private static Timestamp ts(Instant v) {
    return v == null ? null : Timestamp.from(v);
  }

  /**
   * 读取可空 UUID 列。
   *
   * @param r 当前行
   * @param c 列名
   * @return UUID 值，数据库值为空时返回 {@code null}
   * @throws SQLException 列读取失败
   */
  private static UUID uuid(ResultSet r, String c) throws SQLException {
    String v = r.getString(c);
    return v == null ? null : UUID.fromString(v);
  }

  /**
   * 读取可空时间列。
   *
   * @param r 当前行
   * @param c 列名
   * @return 时间值，数据库值为空时返回 {@code null}
   * @throws SQLException 列读取失败
   */
  private static Instant instant(ResultSet r, String c) throws SQLException {
    Timestamp t = r.getTimestamp(c);
    return t == null ? null : t.toInstant();
  }
}
