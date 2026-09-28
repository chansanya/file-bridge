package io.github.chansan.example.web;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.service.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** 普通文件 REST 接口。 */
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
   * 查询文件元数据。
   *
   * @param id 业务文件 ID
   * @return 文件元数据
   */
  @GetMapping("/{id}")
  FileMetadata get(@PathVariable UUID id) {
    return service.get(id);
  }

  /**
   * 流式下载文件。
   *
   * @param id 业务文件 ID
   * @return 带安全文件名、长度和内容类型的流式响应
   */
  /**
   * 流式下载文件。
   *
   * @param id 业务文件 ID
   * @return 流式下载响应
   */
  @GetMapping("/{id}/download")
  ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id) {
    FileResource resource = service.download(id);
    StreamingResponseBody body =
        out -> {
          try (resource) {
            resource.stream().transferTo(out);
          }
        };
    String safe = resource.metadata().originalName().replace("\"", "");
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                Optional.ofNullable(resource.metadata().contentType())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
        .contentLength(resource.metadata().size())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20"))
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
