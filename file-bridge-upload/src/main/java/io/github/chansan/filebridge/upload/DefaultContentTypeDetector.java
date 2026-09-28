package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.spi.ContentTypeDetector;
import java.util.Locale;

/** 基于有限文件头的轻量内容类型检测器。 */
public final class DefaultContentTypeDetector implements ContentTypeDetector {
  /**
   * {@inheritDoc}
   *
   * @param p 有限文件头字节
   * @param name 辅助识别的原始文件名
   * @param declared 客户端声明的内容类型
   * @return 归一化后的内容类型
   */
  @Override
  public String detect(byte[] p, String name, String declared) {
    if (p.length >= 4 && p[0] == (byte) 0x89 && p[1] == 0x50 && p[2] == 0x4e && p[3] == 0x47)
      return "image/png";
    if (p.length >= 3 && p[0] == (byte) 0xff && p[1] == (byte) 0xd8 && p[2] == (byte) 0xff)
      return "image/jpeg";
    if (p.length >= 4 && p[0] == 0x25 && p[1] == 0x50 && p[2] == 0x44 && p[3] == 0x46)
      return "application/pdf";
    if (p.length >= 4 && p[0] == 0x50 && p[1] == 0x4b && p[2] == 0x03 && p[3] == 0x04)
      return "application/zip";
    return declared == null || declared.isBlank()
        ? "application/octet-stream"
        : declared.toLowerCase(Locale.ROOT);
  }
}
