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
public record FileMetadata(
    UUID fileId,
    String originalName,
    long size,
    String sha256,
    String contentType,
    FileReferenceStatus status,
    Instant createdAt) {}
