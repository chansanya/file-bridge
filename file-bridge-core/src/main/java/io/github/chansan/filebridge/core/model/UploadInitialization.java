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
public record UploadInitialization(
    Mode mode, UUID fileId, UUID uploadId, long partSize, int totalParts, Instant expiresAt) {
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
