package io.github.chansan.filebridge.core.model;

/**
 * 存储适配器能力和分片限制。
 *
 * @param multipart 是否支持分片
 * @param signedUrl 是否支持临时地址
 * @param minimumPartSize 最小分片字节数
 * @param maximumPartSize 最大分片字节数
 * @param maximumParts 最大分片数量
 */
public final class StorageCapabilities {
  private final boolean multipart;
  private final boolean signedUrl;
  private final long minimumPartSize;
  private final long maximumPartSize;
  private final int maximumParts;

  public StorageCapabilities(
      boolean multipart,
      boolean signedUrl,
      long minimumPartSize,
      long maximumPartSize,
      int maximumParts) {
    this.multipart = multipart;
    this.signedUrl = signedUrl;
    this.minimumPartSize = minimumPartSize;
    this.maximumPartSize = maximumPartSize;
    this.maximumParts = maximumParts;
  }

  public boolean multipart() {
    return multipart;
  }

  public boolean signedUrl() {
    return signedUrl;
  }

  public long minimumPartSize() {
    return minimumPartSize;
  }

  public long maximumPartSize() {
    return maximumPartSize;
  }

  public int maximumParts() {
    return maximumParts;
  }

  public boolean isMultipart() {
    return multipart;
  }

  public boolean isSignedUrl() {
    return signedUrl;
  }

  public long getMinimumPartSize() {
    return minimumPartSize;
  }

  public long getMaximumPartSize() {
    return maximumPartSize;
  }

  public int getMaximumParts() {
    return maximumParts;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof StorageCapabilities)) return false;
    StorageCapabilities other = (StorageCapabilities) value;
    return multipart == other.multipart
        && signedUrl == other.signedUrl
        && minimumPartSize == other.minimumPartSize
        && maximumPartSize == other.maximumPartSize
        && maximumParts == other.maximumParts;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        multipart, signedUrl, minimumPartSize, maximumPartSize, maximumParts);
  }

  @Override
  public String toString() {
    return "StorageCapabilities{"
        + "multipart="
        + multipart
        + ", signedUrl="
        + signedUrl
        + ", minimumPartSize="
        + minimumPartSize
        + ", maximumPartSize="
        + maximumPartSize
        + ", maximumParts="
        + maximumParts
        + "}";
  }

  /**
   * @return 不支持分片和临时地址的基础能力
   */
  public static StorageCapabilities basic() {
    return new StorageCapabilities(false, false, 0, 0, 0);
  }

  /**
   * 创建分片存储能力。
   *
   * @param min 最小分片字节数
   * @param max 最大分片字节数
   * @param parts 最大分片数量
   * @param signedUrl 是否支持临时地址
   * @return 经过参数校验的能力描述
   */
  public static StorageCapabilities multipart(long min, long max, int parts, boolean signedUrl) {
    if (min <= 0 || max < min || parts <= 0)
      throw new IllegalArgumentException("invalid multipart capabilities");
    return new StorageCapabilities(true, signedUrl, min, max, parts);
  }
}
