package io.github.chansan.filebridge.web;

import io.github.chansan.filebridge.core.error.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 将领域异常映射为稳定 HTTP 错误响应。 */
@RestControllerAdvice
public final class FileBridgeExceptionHandler {
  @ExceptionHandler(FileBridgeException.class)
  ResponseEntity<FileBridgeErrorResponse> handle(FileBridgeException e, HttpServletRequest r) {
    return ResponseEntity.status(status(e.code()))
        .body(new FileBridgeErrorResponse(e.code().name(), e.getMessage(), requestId(r)));
  }

  @ExceptionHandler({IllegalArgumentException.class})
  ResponseEntity<FileBridgeErrorResponse> bad(Exception e, HttpServletRequest r) {
    return ResponseEntity.badRequest()
        .body(new FileBridgeErrorResponse("INVALID_REQUEST", e.getMessage(), requestId(r)));
  }

  private static HttpStatus status(FileBridgeErrorCode c) {
    return switch (c) {
      case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case FILE_NOT_FOUND, UPLOAD_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case ACCESS_DENIED -> HttpStatus.NOT_FOUND;
      case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
      case IDEMPOTENCY_CONFLICT, UPLOAD_PART_CONFLICT, INVALID_UPLOAD_STATE -> HttpStatus.CONFLICT;
      case CAPABILITY_NOT_SUPPORTED -> HttpStatus.NOT_IMPLEMENTED;
      case STORAGE_FAILURE, DATABASE_FAILURE -> HttpStatus.SERVICE_UNAVAILABLE;
      default -> HttpStatus.BAD_REQUEST;
    };
  }

  private static String requestId(HttpServletRequest r) {
    Object v = r.getAttribute(RequestIdFilter.ATTRIBUTE);
    return v == null ? "unknown" : v.toString();
  }
}
