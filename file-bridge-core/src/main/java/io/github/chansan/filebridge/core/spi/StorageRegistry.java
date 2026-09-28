package io.github.chansan.filebridge.core.spi;

import java.util.Collection;

/** 按稳定实例 ID 管理所有存储适配器。 */
public interface StorageRegistry {
  /**
   * 获取指定存储实例。
   *
   * @param storageId 存储实例 ID
   * @return 匹配的存储适配器
   */
  StorageProvider require(String storageId);

  /**
   * @return 不可修改语义的全部已注册存储实例
   */
  Collection<StorageProvider> providers();
}
