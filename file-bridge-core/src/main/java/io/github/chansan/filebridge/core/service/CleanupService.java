package io.github.chansan.filebridge.core.service;

/** 清理过期上传数据和无引用物理对象。 */
public interface CleanupService {
  /**
   * 清理过期且未完成的上传任务。
   *
   * @param limit 单次最多处理的任务数
   * @return 实际清理的任务数
   */
  int cleanExpiredUploads(int limit);

  /**
   * 清理超过保留期且没有有效引用的物理对象。
   *
   * @param limit 单次最多处理的对象数
   * @return 实际删除的对象数
   */
  int cleanUnreferencedObjects(int limit);
}
