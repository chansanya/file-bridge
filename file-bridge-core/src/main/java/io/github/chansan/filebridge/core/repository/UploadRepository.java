package io.github.chansan.filebridge.core.repository;

import io.github.chansan.filebridge.core.model.UploadPart;
import io.github.chansan.filebridge.core.model.UploadTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 上传任务、分片和可恢复租约的持久化端口。 */
public interface UploadRepository {
  /**
   * 保存上传任务。
   *
   * @param task 待保存任务
   * @return 已保存任务
   */
  UploadTask insertTask(UploadTask task);

  /**
   * 查询上传任务。
   *
   * @param uploadId 上传任务 ID
   * @return 上传任务，不存在时返回空
   */
  Optional<UploadTask> findTask(UUID uploadId);

  /**
   * 查询已确认分片。
   *
   * @param uploadId 上传任务 ID
   * @return 已确认成功的分片，按序号升序排列
   */
  List<UploadPart> findCompletedParts(UUID uploadId);

  /**
   * 幂等保存已确认分片。
   *
   * @param part 已确认的分片
   * @return 保存后的分片记录
   */
  UploadPart saveCompletedPart(UploadPart part);

  /**
   * 将首次接收分片的任务切换为上传中。
   *
   * @param uploadId 上传任务 ID
   * @param version 预期乐观锁版本
   * @param now 当前时间
   * @return 条件匹配并成功更新时返回 {@code true}
   */
  boolean markUploading(UUID uploadId, long version, Instant now);

  /**
   * 提交后台完成请求，但不占用工作器租约。
   *
   * @param uploadId 上传任务 ID
   * @param now 当前时间
   * @return 成功切换到等待完成状态时返回 {@code true}
   */
  boolean requestCompletion(UUID uploadId, Instant now);

  /**
   * 通过条件更新获取完成处理租约。
   *
   * @param uploadId 上传任务 ID
   * @param owner 租约持有者
   * @param leaseUntil 租约截止时间
   * @param now 当前时间
   * @return 当前执行者成功持有租约时返回 {@code true}
   */
  boolean acquireCompletionLease(UUID uploadId, String owner, Instant leaseUntil, Instant now);

  /**
   * 续期当前工作器持有的完成租约。
   *
   * @param uploadId 上传任务 ID
   * @param owner 租约持有者
   * @param leaseUntil 新租约截止时间
   * @param now 当前时间
   * @return 当前工作器仍持有租约并续期成功时返回 {@code true}
   */
  boolean renewCompletionLease(UUID uploadId, String owner, Instant leaseUntil, Instant now);

  /**
   * 记录可重试失败并释放租约。
   *
   * @param uploadId 上传任务 ID
   * @param owner 租约持有者
   * @param error 错误摘要
   * @param nextAttemptAt 下次重试时间
   * @param maxAttempts 最大尝试次数
   * @param now 当前时间
   */
  void retryCompletion(
      UUID uploadId,
      String owner,
      String error,
      Instant nextAttemptAt,
      int maxAttempts,
      Instant now);

  /**
   * 将任务切换到最终对象校验阶段。
   *
   * @param uploadId 上传任务 ID
   * @param owner 租约持有者
   * @param now 当前时间
   * @return 状态和租约匹配时返回 {@code true}
   */
  boolean moveToVerifying(UUID uploadId, String owner, Instant now);

  /**
   * 提交唯一完成结果。
   *
   * @param uploadId 上传任务 ID
   * @param owner 租约持有者
   * @param fileId 完成后业务文件 ID
   * @param now 当前时间
   * @return 成功提交结果时返回 {@code true}
   */
  boolean complete(UUID uploadId, String owner, UUID fileId, Instant now);

  /**
   * 取消活动任务。
   *
   * @param uploadId 上传任务 ID
   * @param now 当前时间
   * @return 成功取消时返回 {@code true}
   */
  boolean cancel(UUID uploadId, Instant now);

  /**
   * 标记当前租约持有者处理失败。
   *
   * @param uploadId 上传任务 ID
   * @param owner 租约持有者
   * @param now 当前时间
   */
  void fail(UUID uploadId, String owner, String error, Instant now);

  /**
   * 查询等待完成或租约已经过期的任务。
   *
   * @param now 当前时间
   * @param limit 最大返回数量
   * @return 可由工作器竞争的任务
   */
  List<UploadTask> findCompletableOrExpiredLeases(Instant now, int limit);

  /**
   * 查询已过期且尚未完成的任务。
   *
   * @param now 当前时间
   * @param limit 最大返回数量
   * @return 待清理任务
   */
  List<UploadTask> findExpiredTasks(Instant now, int limit);
}
