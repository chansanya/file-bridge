package io.github.chansan.filebridge.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 持久化的物理对象元数据。
 *
 * @param id 物理对象 ID
 * @param location 存储定位信息
 * @param size 对象字节数
 * @param sha256 服务端校验摘要
 * @param contentType 内容类型
 * @param status 对象状态
 * @param verifiedAt 可信校验完成时间
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param version 乐观锁版本
 */
public record StorageObjectRecord(
    UUID id,
    ObjectLocation location,
    long size,
    String sha256,
    String contentType,
    StorageObjectStatus status,
    Instant verifiedAt,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
