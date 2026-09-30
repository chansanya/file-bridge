package io.github.chansan.filebridge.storage.minio;

import io.github.chansan.filebridge.core.spi.*;
import io.minio.MinioAsyncClient;
import java.util.*;

/** 根据外部配置创建 MinIO 存储适配器。 */
public final class MinioStorageFactory implements StorageProviderFactory {
  /** 禁止实例化工具类。 */

  /**
   * 根据配置创建 MinIO 存储适配器。
   *
   * @param id 存储实例 ID
   * @param c 适配器配置
   * @return MinIO 存储适配器
   */
  @Override
  public Set<String> types() {
    return java.util.Collections.singleton("minio");
  }

  @Override
  public StorageProvider create(String id, Map<String, String> c) {
    return new MinioStorageProvider(
        id,
        required(c, "bucket"),
        MinioAsyncClient.builder()
            .endpoint(required(c, "endpoint"))
            .credentials(required(c, "accessKey"), required(c, "secretKey"))
            .build());
  }

  /**
   * 读取必需配置项。
   *
   * @param c 适配器配置
   * @param k 配置键
   * @return 非空白配置值
   */
  private static String required(Map<String, String> c, String k) {
    String v = c.get(k);
    if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException("Missing MinIO " + k);
    return v;
  }
}
