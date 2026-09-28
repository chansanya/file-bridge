package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 面向业务的文件引用。
 *
 * @param id 业务文件 ID
 * @param objectId 关联物理对象 ID
 * @param tenantId 租户 ID
 * @param ownerId 所有者 ID
 * @param originalName 原始文件名
 * @param businessType 业务类型
 * @param businessId 业务标识
 * @param status 引用状态
 * @param createdAt 创建时间
 * @param deletedAt 逻辑删除时间
 */
public record FileReference(
    UUID id,
    UUID objectId,
    String tenantId,
    String ownerId,
    String originalName,
    String businessType,
    String businessId,
    FileReferenceStatus status,
    Instant createdAt,
    Instant deletedAt) {}
