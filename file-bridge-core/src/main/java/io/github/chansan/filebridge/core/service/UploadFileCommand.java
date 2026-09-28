package io.github.chansan.filebridge.core.service;

/**
 * 普通上传的业务入参。
 *
 * @param originalName 原始文件名
 * @param expectedSize 预期字节数，可为空
 * @param declaredContentType 客户端声明的内容类型
 * @param businessType 业务类型
 * @param businessId 业务标识
 * @param idempotencyKey 幂等键，可为空
 */
public record UploadFileCommand(
    String originalName,
    Long expectedSize,
    String declaredContentType,
    String businessType,
    String businessId,
    String idempotencyKey) {}
