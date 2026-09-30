package io.github.chansan.filebridge.core.spi;

/**
 * 存储平台确认的分片信息。
 *
 * @param partNumber 分片序号
 * @param size 分片字节数
 * @param sha256 服务端计算的分片摘要
 * @param providerPartTag 平台分片标签
 */
public final class UploadedPart {
  private final int partNumber;
  private final long size;
  private final String sha256;
  private final String providerPartTag;

  public UploadedPart(int partNumber, long size, String sha256, String providerPartTag) {
    this.partNumber = partNumber;
    this.size = size;
    this.sha256 = sha256;
    this.providerPartTag = providerPartTag;
  }

  public int partNumber() {
    return partNumber;
  }

  public long size() {
    return size;
  }

  public String sha256() {
    return sha256;
  }

  public String providerPartTag() {
    return providerPartTag;
  }

  public int getPartNumber() {
    return partNumber;
  }

  public long getSize() {
    return size;
  }

  public String getSha256() {
    return sha256;
  }

  public String getProviderPartTag() {
    return providerPartTag;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof UploadedPart)) return false;
    UploadedPart other = (UploadedPart) value;
    return partNumber == other.partNumber
        && size == other.size
        && java.util.Objects.equals(sha256, other.sha256)
        && java.util.Objects.equals(providerPartTag, other.providerPartTag);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(partNumber, size, sha256, providerPartTag);
  }

  @Override
  public String toString() {
    return "UploadedPart{"
        + "partNumber="
        + partNumber
        + ", size="
        + size
        + ", sha256="
        + sha256
        + ", providerPartTag="
        + providerPartTag
        + "}";
  }
}
