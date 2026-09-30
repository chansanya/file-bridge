package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 对外返回的上传任务状态。
 *
 * @param uploadId 上传任务 ID
 * @param status 任务状态
 * @param expectedSize 文件预期字节数
 * @param partSize 分片字节数
 * @param totalParts 分片总数
 * @param completedParts 已确认分片序号
 * @param fileId 完成后生成的业务文件 ID
 * @param expiresAt 任务过期时间
 */
public final class UploadStatusView {
  private final UUID uploadId;
  private final UploadTaskStatus status;
  private final long expectedSize;
  private final long partSize;
  private final int totalParts;
  private final List<Integer> completedParts;
  private final UUID fileId;
  private final Instant expiresAt;

  public UploadStatusView(
      UUID uploadId,
      UploadTaskStatus status,
      long expectedSize,
      long partSize,
      int totalParts,
      List<Integer> completedParts,
      UUID fileId,
      Instant expiresAt) {
    completedParts =
        java.util.Collections.unmodifiableList(new java.util.ArrayList<Integer>(completedParts));

    this.uploadId = uploadId;
    this.status = status;
    this.expectedSize = expectedSize;
    this.partSize = partSize;
    this.totalParts = totalParts;
    this.completedParts = completedParts;
    this.fileId = fileId;
    this.expiresAt = expiresAt;
  }

  public UUID uploadId() {
    return uploadId;
  }

  public UploadTaskStatus status() {
    return status;
  }

  public long expectedSize() {
    return expectedSize;
  }

  public long partSize() {
    return partSize;
  }

  public int totalParts() {
    return totalParts;
  }

  public List<Integer> completedParts() {
    return completedParts;
  }

  public UUID fileId() {
    return fileId;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public UUID getUploadId() {
    return uploadId;
  }

  public UploadTaskStatus getStatus() {
    return status;
  }

  public long getExpectedSize() {
    return expectedSize;
  }

  public long getPartSize() {
    return partSize;
  }

  public int getTotalParts() {
    return totalParts;
  }

  public List<Integer> getCompletedParts() {
    return completedParts;
  }

  public UUID getFileId() {
    return fileId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof UploadStatusView)) return false;
    UploadStatusView other = (UploadStatusView) value;
    return java.util.Objects.equals(uploadId, other.uploadId)
        && java.util.Objects.equals(status, other.status)
        && expectedSize == other.expectedSize
        && partSize == other.partSize
        && totalParts == other.totalParts
        && java.util.Objects.equals(completedParts, other.completedParts)
        && java.util.Objects.equals(fileId, other.fileId)
        && java.util.Objects.equals(expiresAt, other.expiresAt);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        uploadId, status, expectedSize, partSize, totalParts, completedParts, fileId, expiresAt);
  }

  @Override
  public String toString() {
    return "UploadStatusView{"
        + "uploadId="
        + uploadId
        + ", status="
        + status
        + ", expectedSize="
        + expectedSize
        + ", partSize="
        + partSize
        + ", totalParts="
        + totalParts
        + ", completedParts="
        + completedParts
        + ", fileId="
        + fileId
        + ", expiresAt="
        + expiresAt
        + "}";
  }
}
