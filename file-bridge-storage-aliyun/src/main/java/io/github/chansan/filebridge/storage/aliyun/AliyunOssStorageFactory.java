package io.github.chansan.filebridge.storage.aliyun;

import com.aliyun.oss.OSSClientBuilder;
import io.github.chansan.filebridge.core.spi.StorageProvider;
import java.util.Map;

/** 根据外部配置创建阿里云 OSS 存储适配器。 */
public final class AliyunOssStorageFactory {
  /** 禁止实例化工具类。 */
  private AliyunOssStorageFactory() {}

  /**
   * 根据配置创建阿里云 OSS 存储适配器。
   *
   * @param id 存储实例 ID
   * @param c 适配器配置
   * @return 阿里云 OSS 存储适配器
   */
  public static StorageProvider create(String id, Map<String, String> c) {
    return new AliyunOssStorageProvider(
        id,
        required(c, "bucket"),
        new OSSClientBuilder()
            .build(required(c, "endpoint"), required(c, "accessKey"), required(c, "secretKey")));
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
    if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing Aliyun OSS " + k);
    return v;
  }
}
