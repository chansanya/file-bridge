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
public record UploadStatusView(
    UUID uploadId,
    UploadTaskStatus status,
    long expectedSize,
    long partSize,
    int totalParts,
    List<Integer> completedParts,
    UUID fileId,
    Instant expiresAt) {
  public UploadStatusView {
    completedParts = List.copyOf(completedParts);
  }
}
