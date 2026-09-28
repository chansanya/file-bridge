package io.github.chansan.filebridge.core.model;

/**
 * 存储内部的对象定位信息，不应直接暴露给客户端。
 *
 * @param storageId 存储实例 ID
 * @param bucket Bucket 名称，本地存储为空
 * @param objectKey 服务端生成的对象路径
 */
public record ObjectLocation(String storageId, String bucket, String objectKey) {
  public ObjectLocation {
    if (storageId == null || storageId.isBlank())
      throw new IllegalArgumentException("storageId must not be blank");
    if (objectKey == null || objectKey.isBlank())
      throw new IllegalArgumentException("objectKey must not be blank");
  }
}
