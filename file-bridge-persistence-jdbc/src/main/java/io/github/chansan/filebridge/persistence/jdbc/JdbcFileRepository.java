package io.github.chansan.filebridge.persistence.jdbc;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.FileRepository;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;

/** 文件对象和业务引用的 JDBC 仓储实现。 */
public final class JdbcFileRepository implements FileRepository {
  private final NamedParameterJdbcTemplate jdbc;

  /**
   * 创建 JDBC 文件仓储。
   *
   * @param jdbc 具名参数 JDBC 模板
   */
  public JdbcFileRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * {@inheritDoc}
   *
   * @param o 待保存物理对象
   * @return 已保存对象
   */
  @Override
  public StorageObjectRecord insertObject(StorageObjectRecord o) {
    jdbc.update(
        "INSERT INTO"
            + " fb_storage_object(id,storage_id,bucket_name,object_key,size_bytes,sha256,content_type,status,verified_at,created_at,updated_at,version)"
            + " VALUES(:id,:sid,:bucket,:key,:size,:sha,:type,:status,:verified,:created,:updated,:version)",
        new MapSqlParameterSource()
            .addValue("id", s(o.id()))
            .addValue("sid", o.location().storageId())
            .addValue("bucket", o.location().bucket())
            .addValue("key", o.location().objectKey())
            .addValue("size", o.size())
            .addValue("sha", o.sha256())
            .addValue("type", o.contentType())
            .addValue("status", o.status().name())
            .addValue("verified", ts(o.verifiedAt()))
            .addValue("created", ts(o.createdAt()))
            .addValue("updated", ts(o.updatedAt()))
            .addValue("version", o.version()));
    return o;
  }

  /**
   * {@inheritDoc}
   *
   * @param r 待保存业务引用
   * @return 已保存引用
   */
  @Override
  public FileReference insertReference(FileReference reference) {
    int inserted =
        jdbc.update(
            "INSERT INTO"
                + " fb_file_reference(id,object_id,tenant_id,owner_id,original_name,business_type,business_id,status,created_at,deleted_at)"
                + " SELECT :id,:oid,:tenant,:owner,:name,:bt,:bid,:status,:created,:deleted FROM"
                + " fb_storage_object WHERE id=:oid AND status='AVAILABLE'",
            new MapSqlParameterSource()
                .addValue("id", s(reference.id()))
                .addValue("oid", s(reference.objectId()))
                .addValue("tenant", reference.tenantId())
                .addValue("owner", reference.ownerId())
                .addValue("name", reference.originalName())
                .addValue("bt", reference.businessType())
                .addValue("bid", reference.businessId())
                .addValue("status", reference.status().name())
                .addValue("created", ts(reference.createdAt()))
                .addValue("deleted", ts(reference.deletedAt())));
    if (inserted != 1) {
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_UPLOAD_STATE, "Storage object is not available");
    }
    jdbc.update(
        "UPDATE fb_storage_object SET unreferenced_at=NULL,delete_after=NULL,updated_at=:now "
            + "WHERE id=:id AND status='AVAILABLE'",
        JdbcParameters.of("id", s(reference.objectId()), "now", ts(reference.createdAt())));
    return reference;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 业务文件 ID
   * @return 文件引用，不存在时返回空
   */
  @Override
  public Optional<FileReference> findReference(UUID id) {
    return one(
        "SELECT * FROM fb_file_reference WHERE id=:id",
        new MapSqlParameterSource("id", s(id)),
        JdbcFileRepository::ref);
  }

  /**
   * {@inheritDoc}
   *
   * @param id 物理对象 ID
   * @return 物理对象，不存在时返回空
   */
  @Override
  public Optional<StorageObjectRecord> findObject(UUID id) {
    return one(
        "SELECT * FROM fb_storage_object WHERE id=:id",
        new MapSqlParameterSource("id", s(id)),
        JdbcFileRepository::obj);
  }

  /**
   * {@inheritDoc}
   *
   * @param tenant 租户 ID
   * @param owner 用户 ID
   * @param size 文件字节数
   * @param sha 文件 SHA-256
   * @param scope 秒传范围
   * @return 已校验且可用的候选引用
   */
  @Override
  public Optional<FileReference> findReusable(
      String tenant, String owner, long size, String sha, DeduplicationScope scope) {
    String actor = scope == DeduplicationScope.USER ? " AND r.owner_id=:owner" : "";
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenant", tenant)
            .addValue("owner", owner)
            .addValue("size", size)
            .addValue("sha", sha);
    return one(
        "SELECT r.* FROM fb_file_reference r JOIN fb_storage_object o ON o.id=r.object_id WHERE"
            + " r.tenant_id=:tenant"
            + actor
            + " AND r.status='ACTIVE' AND o.status='AVAILABLE' AND o.size_bytes=:size AND"
            + " o.sha256=:sha LIMIT 1",
        p,
        JdbcFileRepository::ref);
  }

  /**
   * {@inheritDoc}
   *
   * @param id 业务文件 ID
   * @param at 删除时间
   * @return 成功从有效状态切换为已删除时返回 {@code true}
   */
  @Override
  public boolean markReferenceDeleted(UUID id, Instant at) {
    int updated =
        jdbc.update(
            "UPDATE fb_file_reference SET status='DELETED',deleted_at=:at "
                + "WHERE id=:id AND status='ACTIVE'",
            JdbcParameters.of("id", s(id), "at", ts(at)));
    if (updated != 1) return false;
    jdbc.update(
        "UPDATE fb_storage_object o JOIN fb_file_reference r ON r.object_id=o.id "
            + "SET o.unreferenced_at=:at,o.updated_at=:at "
            + "WHERE r.id=:id AND o.status='AVAILABLE' "
            + "AND NOT EXISTS(SELECT 1 FROM fb_file_reference active "
            + "WHERE active.object_id=o.id AND active.status='ACTIVE')",
        JdbcParameters.of("id", s(id), "at", ts(at)));
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * @param objectId 物理对象 ID
   * @return 当前有效引用数
   */
  @Override
  public long countActiveReferences(UUID objectId) {
    Long v =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM fb_file_reference WHERE object_id=:id AND status='ACTIVE'",
            JdbcParameters.of("id", s(objectId)),
            Long.class);
    return v == null ? 0 : v;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 物理对象 ID
   * @param now 当前时间
   * @return 成功切换到待删除状态时返回 {@code true}
   */
  @Override
  public boolean markObjectDeletePending(UUID id, Instant now) {
    return jdbc.update(
            "UPDATE fb_storage_object SET status='DELETE_PENDING',updated_at=:now,version=version+1"
                + " WHERE id=:id AND status='AVAILABLE' AND NOT EXISTS(SELECT 1 FROM"
                + " fb_file_reference r WHERE r.object_id=fb_storage_object.id AND"
                + " r.status='ACTIVE')",
            JdbcParameters.of("id", s(id), "now", ts(now)))
        == 1;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 物理对象 ID
   * @param now 当前时间
   */
  @Override
  public void markObjectDeleted(UUID id, Instant now) {
    jdbc.update(
        "UPDATE fb_storage_object SET status='DELETED',updated_at=:now,version=version+1 WHERE"
            + " id=:id AND status='DELETE_PENDING'",
        JdbcParameters.of("id", s(id), "now", ts(now)));
  }

  /**
   * {@inheritDoc}
   *
   * @param olderThan 创建时间上限
   * @param limit 最大返回数量
   * @return 待回收物理对象
   */
  @Override
  public List<StorageObjectRecord> findUnreferencedAvailable(Instant olderThan, int limit) {
    return jdbc.query(
        "SELECT o.* FROM fb_storage_object o WHERE o.status='AVAILABLE' AND o.unreferenced_at IS"
            + " NOT NULL AND o.unreferenced_at<:older AND NOT EXISTS(SELECT 1 FROM"
            + " fb_file_reference r WHERE r.object_id=o.id AND r.status='ACTIVE') ORDER BY"
            + " o.created_at LIMIT "
            + safeLimit(limit),
        JdbcParameters.of("older", ts(olderThan)),
        JdbcFileRepository::obj);
  }

  /**
   * {@inheritDoc}
   *
   * @param status 对象状态
   * @param limit 最大返回数量
   * @return 匹配对象列表
   */
  @Override
  public List<StorageObjectRecord> findObjectsByStatus(StorageObjectStatus status, int limit) {
    return jdbc.query(
        "SELECT * FROM fb_storage_object WHERE status=:status ORDER BY updated_at LIMIT "
            + safeLimit(limit),
        JdbcParameters.of("status", status.name()),
        JdbcFileRepository::obj);
  }

  /**
   * {@inheritDoc}
   *
   * @param id 物理对象 ID
   * @param error 错误摘要
   * @param now 当前时间
   */
  @Override
  public long countReferences(String tenantId, String ownerId, String nameQuery) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT COUNT(*) FROM fb_file_reference WHERE tenant_id=:tenant AND owner_id=:owner AND"
                + " status='ACTIVE'");
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tenant", tenantId).addValue("owner", ownerId);
    if (nameQuery != null && !nameQuery.trim().isEmpty()) {
      sql.append(" AND original_name LIKE :name");
      params.addValue("name", "%" + nameQuery.trim() + "%");
    }
    Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
    return count == null ? 0 : count;
  }

  @Override
  public List<FileReference> findReferences(
      String tenantId, String ownerId, String nameQuery, int offset, int limit) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT * FROM fb_file_reference WHERE tenant_id=:tenant AND owner_id=:owner AND"
                + " status='ACTIVE'");
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("tenant", tenantId)
            .addValue("owner", ownerId)
            .addValue("offset", Math.max(0, offset))
            .addValue("limit", Math.max(1, limit));
    if (nameQuery != null && !nameQuery.trim().isEmpty()) {
      sql.append(" AND original_name LIKE :name");
      params.addValue("name", "%" + nameQuery.trim() + "%");
    }
    sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
    return jdbc.query(sql.toString(), params, JdbcFileRepository::ref);
  }

  @Override
  public void markObjectError(UUID id, String error, Instant now) {
    jdbc.update(
        "UPDATE fb_storage_object SET"
            + " status='ERROR',last_error=:error,updated_at=:now,version=version+1 WHERE id=:id AND"
            + " status<>'DELETED'",
        new MapSqlParameterSource()
            .addValue("id", s(id))
            .addValue(
                "error", error == null ? null : error.substring(0, Math.min(1000, error.length())))
            .addValue("now", ts(now)));
  }

  /**
   * 校验并返回分页上限。
   *
   * @param value 调用方传入的数量上限
   * @return 合法数量上限
   */
  private static int safeLimit(int value) {
    if (value < 1 || value > 1000)
      throw new IllegalArgumentException("limit must be between 1 and 1000");
    return value;
  }

  /**
   * 执行单行查询。
   *
   * @param sql 查询语句
   * @param p SQL 具名参数
   * @param m 行映射器
   * @param <T> 返回记录类型
   * @return 首条记录，查询为空时返回空
   */
  private <T> Optional<T> one(
      String sql, SqlParameterSource p, org.springframework.jdbc.core.RowMapper<T> m) {
    List<T> v = jdbc.query(sql, p, m);
    return v.stream().findFirst();
  }

  /**
   * 将结果集行映射为业务文件引用。
   *
   * @param r 当前行
   * @param n 行序号
   * @return 业务文件引用
   * @throws SQLException 列读取失败
   */
  static FileReference ref(ResultSet r, int n) throws SQLException {
    return new FileReference(
        UUID.fromString(r.getString("id")),
        UUID.fromString(r.getString("object_id")),
        r.getString("tenant_id"),
        r.getString("owner_id"),
        r.getString("original_name"),
        r.getString("business_type"),
        r.getString("business_id"),
        FileReferenceStatus.valueOf(r.getString("status")),
        instant(r, "created_at"),
        instant(r, "deleted_at"));
  }

  /**
   * 将结果集行映射为物理对象记录。
   *
   * @param r 当前行
   * @param n 行序号
   * @return 物理对象记录
   * @throws SQLException 列读取失败
   */
  static StorageObjectRecord obj(ResultSet r, int n) throws SQLException {
    return new StorageObjectRecord(
        UUID.fromString(r.getString("id")),
        new ObjectLocation(
            r.getString("storage_id"), r.getString("bucket_name"), r.getString("object_key")),
        r.getLong("size_bytes"),
        r.getString("sha256"),
        r.getString("content_type"),
        StorageObjectStatus.valueOf(r.getString("status")),
        instant(r, "verified_at"),
        instant(r, "created_at"),
        instant(r, "updated_at"),
        r.getLong("version"));
  }

  /**
   * 转换可空 UUID 为字符串。
   *
   * @param v UUID 值
   * @return UUID 字符串，空值时返回 {@code null}
   */
  static String s(UUID v) {
    return v == null ? null : v.toString();
  }

  /**
   * 转换可空时间戳。
   *
   * @param v 时间值
   * @return JDBC 时间戳，空值时返回 {@code null}
   */
  static Timestamp ts(Instant v) {
    return v == null ? null : Timestamp.from(v);
  }

  /**
   * 读取可空时间列。
   *
   * @param r 当前行
   * @param c 列名
   * @return 时间值，数据库值为空时返回 {@code null}
   * @throws SQLException 列读取失败
   */
  static Instant instant(ResultSet r, String c) throws SQLException {
    Timestamp t = r.getTimestamp(c);
    return t == null ? null : t.toInstant();
  }
}
