package io.github.chansan.filebridge.core.spi;

/**
 * 存储平台分片任务句柄。
 *
 * @param providerUploadId 平台上传 ID
 * @param objectKey 目标对象路径
 */
public final class MultipartUploadHandle {
  private final String providerUploadId;
  private final String objectKey;

  public MultipartUploadHandle(String providerUploadId, String objectKey) {
    this.providerUploadId = providerUploadId;
    this.objectKey = objectKey;
  }

  public String providerUploadId() {
    return providerUploadId;
  }

  public String objectKey() {
    return objectKey;
  }

  public String getProviderUploadId() {
    return providerUploadId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof MultipartUploadHandle)) return false;
    MultipartUploadHandle other = (MultipartUploadHandle) value;
    return java.util.Objects.equals(providerUploadId, other.providerUploadId)
        && java.util.Objects.equals(objectKey, other.objectKey);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(providerUploadId, objectKey);
  }

  @Override
  public String toString() {
    return "MultipartUploadHandle{"
        + "providerUploadId="
        + providerUploadId
        + ", objectKey="
        + objectKey
        + "}";
  }
}
