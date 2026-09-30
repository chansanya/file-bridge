package io.github.chansan.filebridge.storage.tencent;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import io.github.chansan.filebridge.core.spi.*;
import java.util.*;

/** 根据外部配置创建腾讯云 COS 存储适配器。 */
public final class TencentCosStorageFactory implements StorageProviderFactory {
  /** 禁止实例化工具类。 */

  /**
   * 根据配置创建腾讯云 COS 存储适配器。
   *
   * @param id 存储实例 ID
   * @param c 适配器配置
   * @return 腾讯云 COS 存储适配器
   */
  @Override
  public Set<String> types() {
    return new java.util.LinkedHashSet<String>(java.util.Arrays.asList("tencent", "tencent-cos"));
  }

  @Override
  public StorageProvider create(String id, Map<String, String> c) {
    COSClient client =
        new COSClient(
            new BasicCOSCredentials(required(c, "accessKey"), required(c, "secretKey")),
            new ClientConfig(new Region(required(c, "region"))));
    return new TencentCosStorageProvider(id, required(c, "bucket"), client);
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
    if (v == null || v.trim().isEmpty())
      throw new IllegalArgumentException("Missing Tencent COS " + k);
    return v;
  }
}
