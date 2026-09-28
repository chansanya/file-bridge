package io.github.chansan.filebridge.persistence.jdbc;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.repository.IdempotencyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.*;

/** 请求幂等记录的 JDBC 仓储实现。 */
public final class JdbcIdempotencyRepository implements IdempotencyRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcIdempotencyRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

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

  @Override
  public void save(
      String t, String o, String op, String key, String hash, String response, Instant expires) {
    try {
      jdbc.update(
          "INSERT INTO"
              + " fb_idempotency_record(tenant_id,owner_id,operation_name,idempotency_key,request_hash,response_value,expires_at)"
              + " VALUES(:t,:o,:op,:k,:h,:r,:e)",
          new MapSqlParameterSource()
              .addValue("t", t)
              .addValue("o", o)
              .addValue("op", op)
              .addValue("k", key)
              .addValue("h", hash)
              .addValue("r", response)
              .addValue("e", Timestamp.from(expires)));
    } catch (DuplicateKeyException e) {
      Optional<String> existing = findResponse(t, o, op, key, hash);
      if (existing.isEmpty() || !existing.get().equals(response)) throw e;
    }
  }
}
