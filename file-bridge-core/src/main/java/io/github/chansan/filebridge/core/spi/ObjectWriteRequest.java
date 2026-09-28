package io.github.chansan.filebridge.core.spi;

/**
 * 物理对象写入入参。
 *
 * @param objectKey 服务端生成的对象路径
 * @param expectedSize 预期字节数，可为空
 * @param contentType 内容类型
 */
public record ObjectWriteRequest(String objectKey, Long expectedSize, String contentType) {}
