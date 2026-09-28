package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 持久化的分片上传任务。
 *
 * @param id 任务 ID
 * @param tenantId 租户 ID
 * @param ownerId 用户 ID
 * @param storageId 目标存储实例 ID
 * @param objectKey 目标对象路径
 * @param originalName 原始文件名
 * @param businessType 业务类型
 * @param businessId 业务标识
 * @param contentType 内容类型
 * @param expectedSize 预期总字节数
 * @param claimedSha256 客户端声明的完整摘要
 * @param partSize 分片字节数
 * @param totalParts 分片总数
 * @param status 任务状态
 * @param providerUploadId 平台上传 ID
 * @param resultFileId 完成后业务文件 ID
 * @param expiresAt 任务过期时间
 * @param leaseOwner 完成租约持有者
 * @param leaseUntil 租约截止时间
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record UploadTask(
    UUID id,
    String tenantId,
    String ownerId,
    String storageId,
    String objectKey,
    String originalName,
    String businessType,
    String businessId,
    String contentType,
    long expectedSize,
    String claimedSha256,
    long partSize,
    int totalParts,
    UploadTaskStatus status,
    String providerUploadId,
    UUID resultFileId,
    Instant expiresAt,
    String leaseOwner,
    Instant leaseUntil,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
