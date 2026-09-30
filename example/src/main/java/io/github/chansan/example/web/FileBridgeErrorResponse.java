package io.github.chansan.example.web;

/**
 * 统一 HTTP 错误出参。
 *
 * @param code 稳定业务错误码
 * @param message 可读错误信息
 * @param requestId 请求追踪 ID
 */
public final class FileBridgeErrorResponse {
  private final String code;
  private final String message;
  private final String requestId;

  public FileBridgeErrorResponse(String code, String message, String requestId) {
    this.code = code;
    this.message = message;
    this.requestId = requestId;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }

  public String requestId() {
    return requestId;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public String getRequestId() {
    return requestId;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof FileBridgeErrorResponse)) return false;
    FileBridgeErrorResponse other = (FileBridgeErrorResponse) value;
    return java.util.Objects.equals(code, other.code)
        && java.util.Objects.equals(message, other.message)
        && java.util.Objects.equals(requestId, other.requestId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(code, message, requestId);
  }

  @Override
  public String toString() {
    return "FileBridgeErrorResponse{"
        + "code="
        + code
        + ", message="
        + message
        + ", requestId="
        + requestId
        + "}";
  }
}
