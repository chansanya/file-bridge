package io.github.chansan.filebridge.persistence.jdbc;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.FileRepository;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;

/** 文件对象和业务引用的 JDBC 仓储实现。 */
public final class JdbcFileRepository implements FileRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcFileRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

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

  @Override
  public FileReference insertReference(FileReference r) {
    jdbc.update(
        "INSERT INTO"
            + " fb_file_reference(id,object_id,tenant_id,owner_id,original_name,business_type,business_id,status,created_at,deleted_at)"
            + " VALUES(:id,:oid,:tenant,:owner,:name,:bt,:bid,:status,:created,:deleted)",
        new MapSqlParameterSource()
            .addValue("id", s(r.id()))
            .addValue("oid", s(r.objectId()))
            .addValue("tenant", r.tenantId())
            .addValue("owner", r.ownerId())
            .addValue("name", r.originalName())
            .addValue("bt", r.businessType())
            .addValue("bid", r.businessId())
            .addValue("status", r.status().name())
            .addValue("created", ts(r.createdAt()))
            .addValue("deleted", ts(r.deletedAt())));
    return r;
  }

  @Override
  public Optional<FileReference> findReference(UUID id) {
    return one(
        "SELECT * FROM fb_file_reference WHERE id=:id",
        new MapSqlParameterSource("id", s(id)),
        JdbcFileRepository::ref);
  }

  @Override
  public Optional<StorageObjectRecord> findObject(UUID id) {
    return one(
        "SELECT * FROM fb_storage_object WHERE id=:id",
        new MapSqlParameterSource("id", s(id)),
        JdbcFileRepository::obj);
  }

  @Override
  public Optional<FileReference> findReusable(
      String tenant, String owner, long size, String sha, DeduplicationScope scope) {
    String actor = scope == DeduplicationScope.USER ? " AND r.owner_id=:owner" : "";
    var p =
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

  @Override
  public boolean markReferenceDeleted(UUID id, Instant at) {
    return jdbc.update(
            "UPDATE fb_file_reference SET status='DELETED',deleted_at=:at WHERE id=:id AND"
                + " status='ACTIVE'",
            Map.of("id", s(id), "at", ts(at)))
        == 1;
  }

  @Override
  public long countActiveReferences(UUID objectId) {
    Long v =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM fb_file_reference WHERE object_id=:id AND status='ACTIVE'",
            Map.of("id", s(objectId)),
            Long.class);
    return v == null ? 0 : v;
  }

  @Override
  public boolean markObjectDeletePending(UUID id, Instant now) {
    return jdbc.update(
            "UPDATE fb_storage_object SET status='DELETE_PENDING',updated_at=:now,version=version+1"
                + " WHERE id=:id AND status='AVAILABLE' AND NOT EXISTS(SELECT 1 FROM"
                + " fb_file_reference r WHERE r.object_id=fb_storage_object.id AND"
                + " r.status='ACTIVE')",
            Map.of("id", s(id), "now", ts(now)))
        == 1;
  }

  @Override
  public void markObjectDeleted(UUID id, Instant now) {
    jdbc.update(
        "UPDATE fb_storage_object SET status='DELETED',updated_at=:now,version=version+1 WHERE"
            + " id=:id AND status='DELETE_PENDING'",
        Map.of("id", s(id), "now", ts(now)));
  }

  @Override
  public List<StorageObjectRecord> findUnreferencedAvailable(Instant olderThan, int limit) {
    return jdbc.query(
        "SELECT o.* FROM fb_storage_object o WHERE o.status='AVAILABLE' AND o.created_at<:older AND"
            + " NOT EXISTS(SELECT 1 FROM fb_file_reference r WHERE r.object_id=o.id AND"
            + " r.status='ACTIVE') ORDER BY o.created_at LIMIT "
            + safeLimit(limit),
        Map.of("older", ts(olderThan)),
        JdbcFileRepository::obj);
  }

  @Override
  public List<StorageObjectRecord> findObjectsByStatus(StorageObjectStatus status, int limit) {
    return jdbc.query(
        "SELECT * FROM fb_storage_object WHERE status=:status ORDER BY updated_at LIMIT "
            + safeLimit(limit),
        Map.of("status", status.name()),
        JdbcFileRepository::obj);
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

  private static int safeLimit(int value) {
    if (value < 1 || value > 1000)
      throw new IllegalArgumentException("limit must be between 1 and 1000");
    return value;
  }

  private <T> Optional<T> one(
      String sql, SqlParameterSource p, org.springframework.jdbc.core.RowMapper<T> m) {
    List<T> v = jdbc.query(sql, p, m);
    return v.stream().findFirst();
  }

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

  static String s(UUID v) {
    return v == null ? null : v.toString();
  }

  static Timestamp ts(Instant v) {
    return v == null ? null : Timestamp.from(v);
  }

  static Instant instant(ResultSet r, String c) throws SQLException {
    Timestamp t = r.getTimestamp(c);
    return t == null ? null : t.toInstant();
  }
}
