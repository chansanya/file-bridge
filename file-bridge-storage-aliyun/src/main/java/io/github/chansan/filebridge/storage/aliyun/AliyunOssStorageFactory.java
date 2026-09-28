package io.github.chansan.filebridge.storage.aliyun;

import com.aliyun.oss.OSSClientBuilder;
import io.github.chansan.filebridge.core.spi.StorageProvider;
import java.util.Map;

/** 根据外部配置创建阿里云 OSS 存储适配器。 */
public final class AliyunOssStorageFactory {
  private AliyunOssStorageFactory() {}

  public static StorageProvider create(String id, Map<String, String> c) {
    return new AliyunOssStorageProvider(
        id,
        required(c, "bucket"),
        new OSSClientBuilder()
            .build(required(c, "endpoint"), required(c, "accessKey"), required(c, "secretKey")));
  }

  private static String required(Map<String, String> c, String k) {
    String v = c.get(k);
    if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing Aliyun OSS " + k);
    return v;
  }
}
