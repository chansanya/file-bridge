package io.github.chansan.filebridge.core.spi;

/**
 * 存储平台分片任务句柄。
 *
 * @param providerUploadId 平台上传 ID
 * @param objectKey 目标对象路径
 */
public record MultipartUploadHandle(String providerUploadId, String objectKey) {}
