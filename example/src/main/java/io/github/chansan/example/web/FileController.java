package io.github.chansan.example.web;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** 普通文件与分片下载 REST 接口。 */
@RestController
@RequestMapping("${example.file-bridge.base-path:/api/file-bridge}/files")
public final class FileController {
  private final FileService service;

  /**
   * 创建普通文件控制器。
   *
   * @param service 文件业务服务
   */
  public FileController(FileService service) {
    this.service = service;
  }

  /**
   * 普通上传文件。
   *
   * @param file multipart 文件内容
   * @param businessType 业务类型，可为空
   * @param businessId 业务标识，可为空
   * @param key 幂等键，可为空
   * @return 新建文件元数据和 HTTP 201
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<FileMetadata> upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam(required = false) String businessType,
      @RequestParam(required = false) String businessId,
      @RequestHeader(value = "Idempotency-Key", required = false) String key)
      throws Exception {
    FileMetadata result =
        service.upload(
            new UploadFileCommand(
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType(),
                businessType,
                businessId,
                key),
            file.getInputStream());
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  /**
   * 查询文件元数据。
   *
   * @param id 业务文件 ID
   * @return 当前身份有权访问的文件元数据
   */
  /**
   * 分页查询文件资产列表。
   *
   * @param page 页码，默认 1
   * @param size 每页大小，默认 10
   * @param name 文件名过滤关键字，可为空
   * @return 分页结果
   */
  @GetMapping
  PageResult<FileMetadata> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String name) {
    return service.list(page, size, name);
  }

  @GetMapping("/{id}")
  FileMetadata get(@PathVariable UUID id) {
    return service.get(id);
  }

  /**
   * 流式下载文件，支持标准 HTTP Range 分片与断点续传。
   *
   * @param id 业务文件 ID
   * @param rangeHeader 客户端 Range 头，支持分段下载
   * @param request 当前 HTTP 请求，用于向异步下载线程传递 requestId
   * @return 带安全文件名、长度和内容类型的流式响应
   */
  @GetMapping("/{id}/download")
  ResponseEntity<StreamingResponseBody> download(
      @PathVariable UUID id,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
      HttpServletRequest request) {
    FileResource resource = service.download(id);
    String requestId = Objects.toString(request.getAttribute(RequestIdFilter.ATTRIBUTE), "unknown");
    long totalSize = resource.metadata().size();
    String safe = resource.metadata().originalName().replace("\"", "");
    String disposition =
        "attachment; filename*=UTF-8''"
            + java.net.URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
    MediaType mediaType =
        MediaType.parseMediaType(
            Optional.ofNullable(resource.metadata().contentType())
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE));

    if (rangeHeader == null || rangeHeader.isBlank()) {
      StreamingResponseBody body =
          out -> {
            try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId);
                resource) {
              resource.stream().transferTo(out);
            }
          };
      return ResponseEntity.ok()
          .contentType(mediaType)
          .contentLength(totalSize)
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
          .body(body);
    }

    List<HttpRange> ranges;
    try {
      ranges = HttpRange.parseRanges(rangeHeader);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
          .header(HttpHeaders.CONTENT_RANGE, "bytes */" + totalSize)
          .build();
    }

    if (ranges.isEmpty()) {
      return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
          .header(HttpHeaders.CONTENT_RANGE, "bytes */" + totalSize)
          .build();
    }

    HttpRange range = ranges.get(0);
    long start = range.getRangeStart(totalSize);
    long end = range.getRangeEnd(totalSize);
    long rangeLength = end - start + 1;

    StreamingResponseBody body =
        out -> {
          try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId);
              resource) {
            InputStream in = resource.stream();
            long skipped = 0;
            while (skipped < start) {
              long s = in.skip(start - skipped);
              if (s <= 0) break;
              skipped += s;
            }
            byte[] buf = new byte[8192];
            long remaining = rangeLength;
            while (remaining > 0) {
              int read = in.read(buf, 0, (int) Math.min(buf.length, remaining));
              if (read == -1) break;
              out.write(buf, 0, read);
              remaining -= read;
            }
          }
        };

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(mediaType)
        .contentLength(rangeLength)
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalSize)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .body(body);
  }

  /**
   * 创建短期下载地址。
   *
   * @param id 业务文件 ID
   * @param request 有效期请求，可为空
   * @return 包含临时地址的响应
   */
  @PostMapping("/{id}/access-url")
  Map<String, String> url(
      @PathVariable UUID id, @RequestBody(required = false) AccessUrlRequest request) {
    long seconds =
        request == null || request.validitySeconds() == null
            ? 300
            : Math.max(1, Math.min(3600, request.validitySeconds()));
    URI uri = service.createAccessUrl(id, Duration.ofSeconds(seconds));
    return Map.of("url", uri.toString());
  }

  /**
   * 删除业务文件引用。
   *
   * @param id 业务文件 ID
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  /**
   * 临时访问地址请求。
   *
   * @param validitySeconds 有效秒数；为空时使用默认值
   */
  public record AccessUrlRequest(Long validitySeconds) {}
}
