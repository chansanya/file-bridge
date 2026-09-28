package io.github.chansan.filebridge.storage.local;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.spi.*;
import java.util.*;

/** 不可变的存储实例注册表。 */
public final class DefaultStorageRegistry implements StorageRegistry {
  private final Map<String, StorageProvider> providers;

  public DefaultStorageRegistry(Collection<? extends StorageProvider> providers) {
    Map<String, StorageProvider> values = new LinkedHashMap<>();
    for (StorageProvider provider : providers) {
      if (values.putIfAbsent(provider.storageId(), provider) != null)
        throw new FileBridgeException(
            FileBridgeErrorCode.CONFIGURATION_ERROR,
            "Duplicate storage id: " + provider.storageId());
    }
    this.providers = Collections.unmodifiableMap(values);
  }

  @Override
  public StorageProvider require(String storageId) {
    StorageProvider provider = providers.get(storageId);
    if (provider == null)
      throw new FileBridgeException(
          FileBridgeErrorCode.CONFIGURATION_ERROR, "Unknown storage id: " + storageId);
    return provider;
  }

  @Override
  public Collection<StorageProvider> providers() {
    return providers.values();
  }
}
