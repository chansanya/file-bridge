package io.github.chansan.example.web;

import io.github.chansan.filebridge.core.error.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 将领域异常映射为稳定 HTTP 错误响应。 */
@RestControllerAdvice
public final class FileBridgeExceptionHandler {
  /**
   * 处理领域异常并返回稳定错误响应。
   *
   * @param e 领域异常
   * @param r 当前 HTTP 请求
   * @return 稳定错误响应
   */
  @ExceptionHandler(FileBridgeException.class)
  ResponseEntity<FileBridgeErrorResponse> handle(FileBridgeException e, HttpServletRequest r) {
    return ResponseEntity.status(status(e.code()))
        .body(new FileBridgeErrorResponse(e.code().name(), e.getMessage(), requestId(r)));
  }

  /**
   * 处理请求参数非法异常。
   *
   * @param e 参数异常
   * @param r 当前 HTTP 请求
   * @return HTTP 400 错误响应
   */
  @ExceptionHandler({IllegalArgumentException.class})
  ResponseEntity<FileBridgeErrorResponse> bad(Exception e, HttpServletRequest r) {
    return ResponseEntity.badRequest()
        .body(new FileBridgeErrorResponse("INVALID_REQUEST", e.getMessage(), requestId(r)));
  }

  /**
   * 将业务错误码映射为 HTTP 状态码。
   *
   * @param c 业务错误码
   * @return 对应 HTTP 状态码
   */
  private static HttpStatus status(FileBridgeErrorCode c) {
    return switch (c) {
      case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case FILE_NOT_FOUND, UPLOAD_NOT_FOUND, ACCESS_DENIED -> HttpStatus.NOT_FOUND;
      case IDEMPOTENCY_CONFLICT, UPLOAD_PART_CONFLICT, INVALID_UPLOAD_STATE -> HttpStatus.CONFLICT;
      case CAPABILITY_NOT_SUPPORTED -> HttpStatus.NOT_IMPLEMENTED;
      case STORAGE_FAILURE, DATABASE_FAILURE -> HttpStatus.SERVICE_UNAVAILABLE;
      default -> HttpStatus.BAD_REQUEST;
    };
  }

  /**
   * 读取过滤器保存的请求追踪 ID。
   *
   * @param r 当前 HTTP 请求
   * @return 追踪 ID，缺失时返回 {@code unknown}
   */
  private static String requestId(HttpServletRequest r) {
    Object v = r.getAttribute(RequestIdFilter.ATTRIBUTE);
    return v == null ? "unknown" : v.toString();
  }
}
