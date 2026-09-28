package io.github.chansan.filebridge.core.spi;

/** 根据有限文件头和辅助信息识别内容类型。 */
public interface ContentTypeDetector {
  /**
   * 检测文件内容类型。
   *
   * @param prefix 文件开头的有限字节
   * @param originalName 原始文件名
   * @param declaredContentType 客户端声明的内容类型，仅作辅助
   * @return 归一化后的内容类型
   */
  String detect(byte[] prefix, String originalName, String declaredContentType);
}
