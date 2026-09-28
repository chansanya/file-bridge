package io.github.chansan.filebridge.core.service;

/**
 * 初始化分片上传的业务入参。
 *
 * @param originalName 原始文件名
 * @param size 文件总字节数
 * @param sha256 客户端计算的完整摘要，可为空
 * @param contentType 客户端声明的内容类型
 * @param businessType 业务类型
 * @param businessId 业务标识
 * @param idempotencyKey 幂等键，可为空
 */
public record InitializeUploadCommand(
    String originalName,
    long size,
    String sha256,
    String contentType,
    String businessType,
    String businessId,
    String idempotencyKey) {}
