package io.github.chansan.filebridge.core.spi;

import io.github.chansan.filebridge.core.model.Actor;

/** 校验文件大小、并发任务和宿主业务配额。 */
public interface UploadQuotaPolicy {
  /**
   * 校验本次上传是否允许继续。
   *
   * @param actor 当前可信身份
   * @param expectedSize 文件预期字节数
   */
  void check(Actor actor, long expectedSize);
}
