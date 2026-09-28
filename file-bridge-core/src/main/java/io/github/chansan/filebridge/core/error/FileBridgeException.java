package io.github.chansan.filebridge.core.error;

/** 携带稳定业务错误码的领域异常。 */
public class FileBridgeException extends RuntimeException {
  private final FileBridgeErrorCode code;

  /**
   * 创建不包含底层原因的业务异常。
   *
   * @param code 业务错误码
   * @param message 可读错误信息
   */
  public FileBridgeException(FileBridgeErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * 创建保留底层原因的业务异常。
   *
   * @param code 业务错误码
   * @param message 可读错误信息
   * @param cause 原始异常
   */
  public FileBridgeException(FileBridgeErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /**
   * @return 稳定业务错误码
   */
  public FileBridgeErrorCode code() {
    return code;
  }
}
