package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.Actor;
import io.github.chansan.filebridge.core.spi.UploadQuotaPolicy;

/** 基于最大文件字节数的默认配额策略。 */
public final class MaxSizeUploadQuotaPolicy implements UploadQuotaPolicy {
  private final long maxBytes;

  /**
   * 创建最大字节数配额策略。
   *
   * @param maxBytes 允许的最大字节数
   */
  public MaxSizeUploadQuotaPolicy(long maxBytes) {
    if (maxBytes < 0) throw new IllegalArgumentException("maxBytes must not be negative");
    this.maxBytes = maxBytes;
  }

  /**
   * {@inheritDoc}
   *
   * @param actor 当前可信身份
   * @param expectedSize 文件预期字节数
   */
  @Override
  public void check(Actor actor, long expectedSize) {
    if (expectedSize < 0)
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_REQUEST, "File size must not be negative");
    if (expectedSize > maxBytes)
      throw new FileBridgeException(
          FileBridgeErrorCode.FILE_TOO_LARGE, "File exceeds configured size limit");
  }
}
