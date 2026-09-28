package io.github.chansan.filebridge.storage.minio;

import io.github.chansan.filebridge.core.spi.StorageProvider;
import io.minio.MinioAsyncClient;
import java.util.Map;

/** 根据外部配置创建 MinIO 存储适配器。 */
public final class MinioStorageFactory {
  private MinioStorageFactory() {}

  public static StorageProvider create(String id, Map<String, String> c) {
    return new MinioStorageProvider(
        id,
        required(c, "bucket"),
        MinioAsyncClient.builder()
            .endpoint(required(c, "endpoint"))
            .credentials(required(c, "accessKey"), required(c, "secretKey"))
            .build());
  }

  private static String required(Map<String, String> c, String k) {
    String v = c.get(k);
    if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing MinIO " + k);
    return v;
  }
}
