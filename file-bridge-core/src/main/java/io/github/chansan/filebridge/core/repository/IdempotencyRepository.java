package io.github.chansan.filebridge.core.repository;

import java.time.Instant;
import java.util.Optional;

/** 持久化有作用域的请求幂等结果。 */
public interface IdempotencyRepository {
  /**
   * 查询未过期的幂等结果，并校验请求指纹一致性。
   *
   * @param tenantId 租户 ID
   * @param ownerId 用户 ID
   * @param operation 操作类型
   * @param key 客户端幂等键
   * @param requestHash 当前请求指纹
   * @return 已保存的响应值，不存在时返回空
   */
  Optional<String> findResponse(
      String tenantId, String ownerId, String operation, String key, String requestHash);

  /**
   * 保存幂等响应。
   *
   * @param tenantId 租户 ID
   * @param ownerId 用户 ID
   * @param operation 操作类型
   * @param key 客户端幂等键
   * @param requestHash 请求指纹
   * @param responseValue 可恢复业务结果的紧凑响应值
   * @param expiresAt 记录过期时间
   */
  void save(
      String tenantId,
      String ownerId,
      String operation,
      String key,
      String requestHash,
      String responseValue,
      Instant expiresAt);
}
