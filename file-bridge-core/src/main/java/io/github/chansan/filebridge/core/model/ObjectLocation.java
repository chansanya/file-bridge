package io.github.chansan.filebridge.core.model;

/**
 * 存储内部的对象定位信息，不应直接暴露给客户端。
 *
 * @param storageId 存储实例 ID
 * @param bucket Bucket 名称，本地存储为空
 * @param objectKey 服务端生成的对象路径
 */
public final class ObjectLocation {
  private final String storageId;
  private final String bucket;
  private final String objectKey;

  public ObjectLocation(String storageId, String bucket, String objectKey) {
    if (storageId == null || storageId.trim().isEmpty())
      throw new IllegalArgumentException("storageId must not be blank");
    if (objectKey == null || objectKey.trim().isEmpty())
      throw new IllegalArgumentException("objectKey must not be blank");

    this.storageId = storageId;
    this.bucket = bucket;
    this.objectKey = objectKey;
  }

  public String storageId() {
    return storageId;
  }

  public String bucket() {
    return bucket;
  }

  public String objectKey() {
    return objectKey;
  }

  public String getStorageId() {
    return storageId;
  }

  public String getBucket() {
    return bucket;
  }

  public String getObjectKey() {
    return objectKey;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof ObjectLocation)) return false;
    ObjectLocation other = (ObjectLocation) value;
    return java.util.Objects.equals(storageId, other.storageId)
        && java.util.Objects.equals(bucket, other.bucket)
        && java.util.Objects.equals(objectKey, other.objectKey);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(storageId, bucket, objectKey);
  }

  @Override
  public String toString() {
    return "ObjectLocation{"
        + "storageId="
        + storageId
        + ", bucket="
        + bucket
        + ", objectKey="
        + objectKey
        + "}";
  }
}
