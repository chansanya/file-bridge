package io.github.chansan.filebridge.core.service;

/** 对账数据库元数据与物理存储状态。 */
public interface ReconciliationService {
  /**
   * 执行一批保守对账，只标记可以明确判断的问题。
   *
   * @param limit 单次最多检查的对象数
   * @return 本次发现的问题数
   */
  int reconcile(int limit);
}
