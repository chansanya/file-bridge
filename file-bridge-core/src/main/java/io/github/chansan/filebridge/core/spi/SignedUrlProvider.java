package io.github.chansan.filebridge.core.spi;

import io.github.chansan.filebridge.core.model.ObjectLocation;
import java.net.URI;
import java.time.Duration;

/** 为已通过业务鉴权的对象生成临时下载地址。 */
public interface SignedUrlProvider {
  /**
   * 创建短期下载地址。
   *
   * @param location 可信的对象定位信息
   * @param validity 地址有效时长
   * @return 临时下载 URI
   */
  URI createDownloadUrl(ObjectLocation location, Duration validity);
}
