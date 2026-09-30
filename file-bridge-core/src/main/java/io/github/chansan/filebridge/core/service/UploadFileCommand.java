package io.github.chansan.filebridge.core.service;

/**
 * 普通上传的业务入参。
 *
 * @param originalName 原始文件名
 * @param expectedSize 预期字节数，可为空
 * @param declaredContentType 客户端声明的内容类型
 * @param businessType 业务类型
 * @param businessId 业务标识
 * @param idempotencyKey 幂等键，可为空
 */
public final class UploadFileCommand {
  private final String originalName;
  private final Long expectedSize;
  private final String declaredContentType;
  private final String businessType;
  private final String businessId;
  private final String idempotencyKey;

  public UploadFileCommand(
      String originalName,
      Long expectedSize,
      String declaredContentType,
      String businessType,
      String businessId,
      String idempotencyKey) {
    this.originalName = originalName;
    this.expectedSize = expectedSize;
    this.declaredContentType = declaredContentType;
    this.businessType = businessType;
    this.businessId = businessId;
    this.idempotencyKey = idempotencyKey;
  }

  public String originalName() {
    return originalName;
  }

  public Long expectedSize() {
    return expectedSize;
  }

  public String declaredContentType() {
    return declaredContentType;
  }

  public String businessType() {
    return businessType;
  }

  public String businessId() {
    return businessId;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public String getOriginalName() {
    return originalName;
  }

  public Long getExpectedSize() {
    return expectedSize;
  }

  public String getDeclaredContentType() {
    return declaredContentType;
  }

  public String getBusinessType() {
    return businessType;
  }

  public String getBusinessId() {
    return businessId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof UploadFileCommand)) return false;
    UploadFileCommand other = (UploadFileCommand) value;
    return java.util.Objects.equals(originalName, other.originalName)
        && java.util.Objects.equals(expectedSize, other.expectedSize)
        && java.util.Objects.equals(declaredContentType, other.declaredContentType)
        && java.util.Objects.equals(businessType, other.businessType)
        && java.util.Objects.equals(businessId, other.businessId)
        && java.util.Objects.equals(idempotencyKey, other.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        originalName, expectedSize, declaredContentType, businessType, businessId, idempotencyKey);
  }

  @Override
  public String toString() {
    return "UploadFileCommand{"
        + "originalName="
        + originalName
        + ", expectedSize="
        + expectedSize
        + ", declaredContentType="
        + declaredContentType
        + ", businessType="
        + businessType
        + ", businessId="
        + businessId
        + ", idempotencyKey="
        + idempotencyKey
        + "}";
  }
}
