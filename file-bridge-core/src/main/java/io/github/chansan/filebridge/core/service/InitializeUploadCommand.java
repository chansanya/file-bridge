package io.github.chansan.filebridge.core.service;

/**
 * 初始化分片上传的业务入参。
 *
 * @param originalName 原始文件名
 * @param size 文件总字节数
 * @param sha256 客户端计算的完整摘要，可为空
 * @param contentType 客户端声明的内容类型
 * @param businessType 业务类型
 * @param businessId 业务标识
 * @param idempotencyKey 幂等键，可为空
 */
public final class InitializeUploadCommand {
  private final String originalName;
  private final long size;
  private final String sha256;
  private final String contentType;
  private final String businessType;
  private final String businessId;
  private final String idempotencyKey;

  public InitializeUploadCommand(
      String originalName,
      long size,
      String sha256,
      String contentType,
      String businessType,
      String businessId,
      String idempotencyKey) {
    this.originalName = originalName;
    this.size = size;
    this.sha256 = sha256;
    this.contentType = contentType;
    this.businessType = businessType;
    this.businessId = businessId;
    this.idempotencyKey = idempotencyKey;
  }

  public String originalName() {
    return originalName;
  }

  public long size() {
    return size;
  }

  public String sha256() {
    return sha256;
  }

  public String contentType() {
    return contentType;
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

  public long getSize() {
    return size;
  }

  public String getSha256() {
    return sha256;
  }

  public String getContentType() {
    return contentType;
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
    if (!(value instanceof InitializeUploadCommand)) return false;
    InitializeUploadCommand other = (InitializeUploadCommand) value;
    return java.util.Objects.equals(originalName, other.originalName)
        && size == other.size
        && java.util.Objects.equals(sha256, other.sha256)
        && java.util.Objects.equals(contentType, other.contentType)
        && java.util.Objects.equals(businessType, other.businessType)
        && java.util.Objects.equals(businessId, other.businessId)
        && java.util.Objects.equals(idempotencyKey, other.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        originalName, size, sha256, contentType, businessType, businessId, idempotencyKey);
  }

  @Override
  public String toString() {
    return "InitializeUploadCommand{"
        + "originalName="
        + originalName
        + ", size="
        + size
        + ", sha256="
        + sha256
        + ", contentType="
        + contentType
        + ", businessType="
        + businessType
        + ", businessId="
        + businessId
        + ", idempotencyKey="
        + idempotencyKey
        + "}";
  }
}
