package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 上传初始化结果，明确区分秒传和分片上传。
 *
 * @param mode 初始化模式
 * @param fileId 秒传生成的业务文件 ID
 * @param uploadId 分片上传任务 ID
 * @param partSize 分片字节数
 * @param totalParts 分片总数
 * @param expiresAt 任务过期时间
 */
public final class UploadInitialization {
  private final Mode mode;
  private final UUID fileId;
  private final UUID uploadId;
  private final long partSize;
  private final int totalParts;
  private final Instant expiresAt;

  public UploadInitialization(
      Mode mode, UUID fileId, UUID uploadId, long partSize, int totalParts, Instant expiresAt) {
    this.mode = mode;
    this.fileId = fileId;
    this.uploadId = uploadId;
    this.partSize = partSize;
    this.totalParts = totalParts;
    this.expiresAt = expiresAt;
  }

  public Mode mode() {
    return mode;
  }

  public UUID fileId() {
    return fileId;
  }

  public UUID uploadId() {
    return uploadId;
  }

  public long partSize() {
    return partSize;
  }

  public int totalParts() {
    return totalParts;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Mode getMode() {
    return mode;
  }

  public UUID getFileId() {
    return fileId;
  }

  public UUID getUploadId() {
    return uploadId;
  }

  public long getPartSize() {
    return partSize;
  }

  public int getTotalParts() {
    return totalParts;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof UploadInitialization)) return false;
    UploadInitialization other = (UploadInitialization) value;
    return java.util.Objects.equals(mode, other.mode)
        && java.util.Objects.equals(fileId, other.fileId)
        && java.util.Objects.equals(uploadId, other.uploadId)
        && partSize == other.partSize
        && totalParts == other.totalParts
        && java.util.Objects.equals(expiresAt, other.expiresAt);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(mode, fileId, uploadId, partSize, totalParts, expiresAt);
  }

  @Override
  public String toString() {
    return "UploadInitialization{"
        + "mode="
        + mode
        + ", fileId="
        + fileId
        + ", uploadId="
        + uploadId
        + ", partSize="
        + partSize
        + ", totalParts="
        + totalParts
        + ", expiresAt="
        + expiresAt
        + "}";
  }

  public enum Mode {
    INSTANT,
    UPLOAD
  }

  /**
   * 创建秒传结果。
   *
   * @param fileId 新创建的业务文件 ID
   * @return 秒传初始化结果
   */
  public static UploadInitialization instant(UUID fileId) {
    return new UploadInitialization(Mode.INSTANT, fileId, null, 0, 0, null);
  }

  /**
   * 创建分片上传结果。
   *
   * @param uploadId 上传任务 ID
   * @param partSize 分片字节数
   * @param totalParts 分片总数
   * @param expiresAt 任务过期时间
   * @return 分片上传初始化结果
   */
  public static UploadInitialization upload(
      UUID uploadId, long partSize, int totalParts, Instant expiresAt) {
    return new UploadInitialization(Mode.UPLOAD, null, uploadId, partSize, totalParts, expiresAt);
  }
}
