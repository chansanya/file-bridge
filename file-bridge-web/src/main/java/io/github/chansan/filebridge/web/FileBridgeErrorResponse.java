package io.github.chansan.filebridge.web;

/**
 * 统一 HTTP 错误出参。
 *
 * @param code 稳定业务错误码
 * @param message 可读错误信息
 * @param requestId 请求追踪 ID
 */
public record FileBridgeErrorResponse(String code, String message, String requestId) {}
