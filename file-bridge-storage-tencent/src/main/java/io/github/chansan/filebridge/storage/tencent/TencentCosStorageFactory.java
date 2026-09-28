package io.github.chansan.filebridge.storage.tencent;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import io.github.chansan.filebridge.core.spi.StorageProvider;
import java.util.Map;

/** 根据外部配置创建腾讯云 COS 存储适配器。 */
public final class TencentCosStorageFactory {
  private TencentCosStorageFactory() {}

  public static StorageProvider create(String id, Map<String, String> c) {
    COSClient client =
        new COSClient(
            new BasicCOSCredentials(required(c, "accessKey"), required(c, "secretKey")),
            new ClientConfig(new Region(required(c, "region"))));
    return new TencentCosStorageProvider(id, required(c, "bucket"), client);
  }

  private static String required(Map<String, String> c, String k) {
    String v = c.get(k);
    if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing Tencent COS " + k);
    return v;
  }
}
