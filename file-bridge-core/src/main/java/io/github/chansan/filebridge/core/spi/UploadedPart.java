package io.github.chansan.filebridge.core.spi;

/**
 * 存储平台确认的分片信息。
 *
 * @param partNumber 分片序号
 * @param size 分片字节数
 * @param sha256 服务端计算的分片摘要
 * @param providerPartTag 平台分片标签
 */
public record UploadedPart(int partNumber, long size, String sha256, String providerPartTag) {}
