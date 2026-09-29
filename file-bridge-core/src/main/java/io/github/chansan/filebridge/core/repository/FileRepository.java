package io.github.chansan.filebridge.core.repository;

import io.github.chansan.filebridge.core.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 文件物理对象和业务引用的持久化端口。 */
public interface FileRepository {
  /**
   * 保存物理对象。
   *
   * @param object 待保存物理对象
   * @return 已保存对象
   */
  StorageObjectRecord insertObject(StorageObjectRecord object);

  /**
   * 保存业务文件引用。
   *
   * @param reference 待保存业务引用
   * @return 已保存引用
   */
  FileReference insertReference(FileReference reference);

  /**
   * 查询业务文件引用。
   *
   * @param fileId 业务文件 ID
   * @return 文件引用，不存在时返回空
   */
  Optional<FileReference> findReference(UUID fileId);

  /**
   * 查询物理对象。
   *
   * @param objectId 物理对象 ID
   * @return 物理对象，不存在时返回空
   */
  Optional<StorageObjectRecord> findObject(UUID objectId);

  /**
   * 在指定授权范围内查询可秒传引用。
   *
   * @param tenantId 租户 ID
   * @param ownerId 用户 ID
   * @param size 文件字节数
   * @param sha256 文件 SHA-256
   * @param scope 秒传范围
   * @return 已校验且可用的候选引用
   */
  Optional<FileReference> findReusable(
      String tenantId, String ownerId, long size, String sha256, DeduplicationScope scope);

  /**
   * 逻辑删除业务引用。
   *
   * @param fileId 业务文件 ID
   * @param deletedAt 删除时间
   * @return 成功从有效状态切换为已删除时返回 {@code true}
   */
  boolean markReferenceDeleted(UUID fileId, Instant deletedAt);

  /**
   * 统计物理对象的有效引用。
   *
   * @param objectId 物理对象 ID
   * @return 当前有效引用数
   */
  long countActiveReferences(UUID objectId);

  /**
   * 原子抢占无引用对象删除权。
   *
   * @param objectId 物理对象 ID
   * @param now 当前时间
   * @return 成功切换到待删除状态时返回 {@code true}
   */
  boolean markObjectDeletePending(UUID objectId, Instant now);

  /**
   * 标记物理对象已经删除。
   *
   * @param objectId 物理对象 ID
   * @param now 当前时间
   */
  void markObjectDeleted(UUID objectId, Instant now);

  /**
   * 查询超过保留时间且没有有效引用的对象。
   *
   * @param olderThan 创建时间上限
   * @param limit 最大返回数量
   * @return 待回收物理对象
   */
  List<StorageObjectRecord> findUnreferencedAvailable(Instant olderThan, int limit);

  /**
   * 按状态查询物理对象。
   *
   * @param status 对象状态
   * @param limit 最大返回数量
   * @return 匹配对象列表
   */
  List<StorageObjectRecord> findObjectsByStatus(StorageObjectStatus status, int limit);

  /**
   * 标记对象异常并记录原因。
   *
   * @param objectId 物理对象 ID
   * @param error 错误摘要
   * @param now 当前时间
   */
  void markObjectError(UUID objectId, String error, Instant now);

  /**
   * 统计有效业务引用总数。
   *
   * @param tenantId 租户 ID
   * @param ownerId 用户 ID
   * @param nameQuery 文件名过滤关键字，为空时不限制
   * @return 匹配的有效引用数
   */
  long countReferences(String tenantId, String ownerId, String nameQuery);

  /**
   * 分页查询有效业务引用。
   *
   * @param tenantId 租户 ID
   * @param ownerId 用户 ID
   * @param nameQuery 文件名过滤关键字，为空时不限制
   * @param offset 偏移量
   * @param limit 获取数量
   * @return 业务引用列表
   */
  List<FileReference> findReferences(
      String tenantId, String ownerId, String nameQuery, int offset, int limit);
}
