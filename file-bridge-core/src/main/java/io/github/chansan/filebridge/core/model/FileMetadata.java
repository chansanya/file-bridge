package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 对外返回的文件元数据。
 *
 * @param fileId 业务文件 ID
 * @param originalName 原始文件名
 * @param size 文件字节数
 * @param sha256 服务端校验的 SHA-256
 * @param contentType 内容类型
 * @param status 业务引用状态
 * @param createdAt 创建时间
 */
public final class FileMetadata {
  private final UUID fileId;
  private final String originalName;
  private final long size;
  private final String sha256;
  private final String contentType;
  private final FileReferenceStatus status;
  private final Instant createdAt;

  public FileMetadata(
      UUID fileId,
      String originalName,
      long size,
      String sha256,
      String contentType,
      FileReferenceStatus status,
      Instant createdAt) {
    this.fileId = fileId;
    this.originalName = originalName;
    this.size = size;
    this.sha256 = sha256;
    this.contentType = contentType;
    this.status = status;
    this.createdAt = createdAt;
  }

  public UUID fileId() {
    return fileId;
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

  public FileReferenceStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public UUID getFileId() {
    return fileId;
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

  public FileReferenceStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof FileMetadata)) return false;
    FileMetadata other = (FileMetadata) value;
    return java.util.Objects.equals(fileId, other.fileId)
        && java.util.Objects.equals(originalName, other.originalName)
        && size == other.size
        && java.util.Objects.equals(sha256, other.sha256)
        && java.util.Objects.equals(contentType, other.contentType)
        && java.util.Objects.equals(status, other.status)
        && java.util.Objects.equals(createdAt, other.createdAt);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        fileId, originalName, size, sha256, contentType, status, createdAt);
  }

  @Override
  public String toString() {
    return "FileMetadata{"
        + "fileId="
        + fileId
        + ", originalName="
        + originalName
        + ", size="
        + size
        + ", sha256="
        + sha256
        + ", contentType="
        + contentType
        + ", status="
        + status
        + ", createdAt="
        + createdAt
        + "}";
  }
}
