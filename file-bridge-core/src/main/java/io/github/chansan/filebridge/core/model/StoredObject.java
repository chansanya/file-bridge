package io.github.chansan.filebridge.core.model;

import java.time.Instant;

/**
 * 存储适配器完成写入后返回的对象元数据。
 *
 * @param location 对象定位信息
 * @param size 实际写入字节数
 * @param sha256 写入过程中计算的摘要
 * @param contentType 内容类型
 * @param createdAt 写入时间
 */
public record StoredObject(
    ObjectLocation location, long size, String sha256, String contentType, Instant createdAt) {
  public StoredObject {
    if (size < 0) throw new IllegalArgumentException("size must not be negative");
  }
}
