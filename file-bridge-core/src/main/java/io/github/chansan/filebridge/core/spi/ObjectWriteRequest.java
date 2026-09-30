package io.github.chansan.filebridge.core.spi;

/**
 * 物理对象写入入参。
 *
 * @param objectKey 服务端生成的对象路径
 * @param expectedSize 预期字节数，可为空
 * @param contentType 内容类型
 */
public final class ObjectWriteRequest {
  private final String objectKey;
  private final Long expectedSize;
  private final String contentType;

  public ObjectWriteRequest(String objectKey, Long expectedSize, String contentType) {
    this.objectKey = objectKey;
    this.expectedSize = expectedSize;
    this.contentType = contentType;
  }

  public String objectKey() {
    return objectKey;
  }

  public Long expectedSize() {
    return expectedSize;
  }

  public String contentType() {
    return contentType;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public Long getExpectedSize() {
    return expectedSize;
  }

  public String getContentType() {
    return contentType;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof ObjectWriteRequest)) return false;
    ObjectWriteRequest other = (ObjectWriteRequest) value;
    return java.util.Objects.equals(objectKey, other.objectKey)
        && java.util.Objects.equals(expectedSize, other.expectedSize)
        && java.util.Objects.equals(contentType, other.contentType);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(objectKey, expectedSize, contentType);
  }

  @Override
  public String toString() {
    return "ObjectWriteRequest{"
        + "objectKey="
        + objectKey
        + ", expectedSize="
        + expectedSize
        + ", contentType="
        + contentType
        + "}";
  }
}
