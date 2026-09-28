package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 服务端确认的上传分片记录。
 *
 * @param id 分片记录 ID
 * @param uploadId 上传任务 ID
 * @param partNumber 从 1 开始的分片序号
 * @param size 实际字节数
 * @param sha256 分片摘要
 * @param providerPartTag 平台分片标签
 * @param status 分片状态
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record UploadPart(
    UUID id,
    UUID uploadId,
    int partNumber,
    long size,
    String sha256,
    String providerPartTag,
    UploadPartStatus status,
    Instant createdAt,
    Instant updatedAt) {}
