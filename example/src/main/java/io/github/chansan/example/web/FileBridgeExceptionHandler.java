package io.github.chansan.example.web;

import io.github.chansan.filebridge.core.error.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 将领域异常映射为稳定 HTTP 错误响应。 */
@RestControllerAdvice
public final class FileBridgeExceptionHandler {
  /** 处理领域异常。 */
  @ExceptionHandler(FileBridgeException.class)
  ResponseEntity<FileBridgeErrorResponse> handle(
      FileBridgeException e, HttpServletRequest request) {
    return response(status(e.code()), e.code().name(), e.getMessage(), request);
  }

  /** 处理参数绑定、请求体解析和校验异常。 */
  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class,
    MissingRequestHeaderException.class,
    HttpMessageNotReadableException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<FileBridgeErrorResponse> badRequest(Exception e, HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), request);
  }

  /** 处理 Servlet multipart 大小限制。 */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<FileBridgeErrorResponse> tooLarge(
      MaxUploadSizeExceededException e, HttpServletRequest request) {
    return response(
        HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds upload size limit", request);
  }

  private static HttpStatus status(FileBridgeErrorCode code) {
    switch (code) {
      case UNAUTHENTICATED:
        return HttpStatus.UNAUTHORIZED;
      case FILE_NOT_FOUND:
      case UPLOAD_NOT_FOUND:
      case ACCESS_DENIED:
        return HttpStatus.NOT_FOUND;
      case IDEMPOTENCY_CONFLICT:
      case UPLOAD_PART_CONFLICT:
      case INVALID_UPLOAD_STATE:
        return HttpStatus.CONFLICT;
      case FILE_TOO_LARGE:
        return HttpStatus.PAYLOAD_TOO_LARGE;
      case CAPABILITY_NOT_SUPPORTED:
        return HttpStatus.NOT_IMPLEMENTED;
      case STORAGE_FAILURE:
      case DATABASE_FAILURE:
        return HttpStatus.SERVICE_UNAVAILABLE;
      default:
        return HttpStatus.BAD_REQUEST;
    }
  }

  private static ResponseEntity<FileBridgeErrorResponse> response(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(new FileBridgeErrorResponse(code, message, requestId(request)));
  }

  private static String requestId(HttpServletRequest request) {
    Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
    return value == null ? "unknown" : value.toString();
  }
}
