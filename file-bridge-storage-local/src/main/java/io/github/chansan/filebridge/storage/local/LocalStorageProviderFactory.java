package io.github.chansan.filebridge.storage.local;

import io.github.chansan.filebridge.core.spi.*;
import java.util.*;

/** 创建本地文件系统存储适配器。 */
public final class LocalStorageProviderFactory implements StorageProviderFactory {
  @Override
  public Set<String> types() {
    return java.util.Collections.singleton("local");
  }

  @Override
  public StorageProvider create(String storageId, Map<String, String> configuration) {
    return new LocalStorageProvider(
        storageId,
        Paths.get(required(configuration, "rootPath")),
        Paths.get(required(configuration, "tempPath")));
  }

  private static String required(Map<String, String> configuration, String key) {
    String value = configuration.get(key);
    if (value == null || value.trim().isEmpty())
      throw new IllegalArgumentException("Missing " + key);
    return value;
  }
}
