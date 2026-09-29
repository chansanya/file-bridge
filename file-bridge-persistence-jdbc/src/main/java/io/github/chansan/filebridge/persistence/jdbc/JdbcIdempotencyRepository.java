package io.github.chansan.filebridge.persistence.jdbc;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.repository.IdempotencyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;

/** 请求幂等记录的 JDBC 仓储实现。 */
public final class JdbcIdempotencyRepository implements IdempotencyRepository {
  private final NamedParameterJdbcTemplate jdbc;

  /**
   * 创建幂等记录仓储。
   *
   * @param jdbc 具名参数 JDBC 模板
   */
  public JdbcIdempotencyRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * {@inheritDoc}
   *
   * @param t 租户 ID
   * @param o 用户 ID
   * @param op 操作类型
   * @param key 客户端幂等键
   * @param hash 当前请求指纹
   * @return 已保存的响应值，不存在时返回空
   */
  @Override
  public Optional<String> findResponse(String t, String o, String op, String key, String hash) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT request_hash,response_value FROM fb_idempotency_record WHERE tenant_id=:t AND"
                + " owner_id=:o AND operation_name=:op AND idempotency_key=:k AND"
                + " expires_at>CURRENT_TIMESTAMP(6)",
            Map.of("t", t, "o", o, "op", op, "k", key));
    if (rows.isEmpty()) return Optional.empty();
    if (!hash.equals(rows.get(0).get("request_hash")))
      throw new FileBridgeException(
          FileBridgeErrorCode.IDEMPOTENCY_CONFLICT,
          "Idempotency key was reused with a different request");
    return Optional.of((String) rows.get(0).get("response_value"));
  }

  /**
   * {@inheritDoc}
   *
   * @param t 租户 ID
   * @param o 用户 ID
   * @param op 操作类型
   * @param key 客户端幂等键
   * @param hash 请求指纹
   * @param response 可恢复业务结果的紧凑响应值
   * @param expires 记录过期时间
   */
  @Override
  public String save(
      String t, String o, String op, String key, String hash, String response, Instant expires) {
    jdbc.update(
        "INSERT INTO fb_idempotency_record"
            + "(tenant_id,owner_id,operation_name,idempotency_key,request_hash,response_value,expires_at) "
            + "VALUES(:t,:o,:op,:k,:h,:r,:e) "
            + "ON DUPLICATE KEY UPDATE "
            + "request_hash=IF(fb_idempotency_record.expires_at<=CURRENT_TIMESTAMP(6),VALUES(request_hash),fb_idempotency_record.request_hash),"
            + "response_value=IF(fb_idempotency_record.expires_at<=CURRENT_TIMESTAMP(6),VALUES(response_value),fb_idempotency_record.response_value),"
            + "expires_at=IF(fb_idempotency_record.expires_at<=CURRENT_TIMESTAMP(6),VALUES(expires_at),fb_idempotency_record.expires_at)",
        new MapSqlParameterSource()
            .addValue("t", t)
            .addValue("o", o)
            .addValue("op", op)
            .addValue("k", key)
            .addValue("h", hash)
            .addValue("r", response)
            .addValue("e", Timestamp.from(expires)));
    return findResponse(t, o, op, key, hash).orElseThrow();
  }
}
