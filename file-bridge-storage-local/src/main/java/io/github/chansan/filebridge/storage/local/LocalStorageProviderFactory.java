package io.github.chansan.filebridge.storage.local;

import io.github.chansan.filebridge.core.spi.*;
import java.nio.file.Path;
import java.util.*;

/** 创建本地文件系统存储适配器。 */
public final class LocalStorageProviderFactory implements StorageProviderFactory {
  @Override
  public Set<String> types() {
    return Set.of("local");
  }

  @Override
  public StorageProvider create(String storageId, Map<String, String> configuration) {
    return new LocalStorageProvider(
        storageId,
        Path.of(required(configuration, "rootPath")),
        Path.of(required(configuration, "tempPath")));
  }

  private static String required(Map<String, String> configuration, String key) {
    String value = configuration.get(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
    return value;
  }
}
