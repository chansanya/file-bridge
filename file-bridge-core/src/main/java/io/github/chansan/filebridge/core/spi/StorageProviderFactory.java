package io.github.chansan.filebridge.core.spi;

import java.util.Map;
import java.util.Set;

/** 按存储类型创建适配器，云模块通过 Java ServiceLoader 提供实现。 */
public interface StorageProviderFactory {
  /**
   * @return 当前工厂支持的配置类型名称
   */
  Set<String> types();

  /**
   * 创建存储适配器。
   *
   * @param storageId 稳定存储实例 ID
   * @param configuration 已过滤的字符串配置
   * @return 存储适配器
   */
  StorageProvider create(String storageId, Map<String, String> configuration);
}
