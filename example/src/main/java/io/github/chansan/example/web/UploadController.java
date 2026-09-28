package io.github.chansan.example.web;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 分片上传 REST 接口。 */
@RestController
@RequestMapping("${example.file-bridge.base-path:/api/file-bridge}/uploads")
public final class UploadController {
  private final UploadService service;

  /**
   * 创建分片上传控制器。
   *
   * @param service 分片上传业务服务
   */
  public UploadController(UploadService service) {
    this.service = service;
  }

  /**
   * 检查秒传并初始化分片任务。
   *
   * @param r 文件初始化参数
   * @param key 幂等键，可为空
   * @return 秒传结果或上传任务规则
   */
  @PostMapping
  ResponseEntity<UploadInitialization> init(
      @Valid @RequestBody InitializeRequest r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    UploadInitialization v =
        service.initialize(
            new InitializeUploadCommand(
                r.originalName(),
                r.size(),
                r.sha256(),
                r.contentType(),
                r.businessType(),
                r.businessId(),
                key));
    return ResponseEntity.status(
            v.mode() == UploadInitialization.Mode.INSTANT ? HttpStatus.OK : HttpStatus.CREATED)
        .body(v);
  }

  /**
   * 上传一个原始二进制分片。
   *
   * @param id 上传任务 ID
   * @param part 从 1 开始的分片序号
   * @param length 请求体长度
   * @param sha 客户端声明的分片摘要，可为空
   * @param req Servlet 请求，用于流式读取正文
   */
  @PutMapping(path = "/{id}/parts/{part}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void part(
      @PathVariable UUID id,
      @PathVariable int part,
      @RequestHeader(HttpHeaders.CONTENT_LENGTH) long length,
      @RequestHeader(value = "X-Part-Sha256", required = false) String sha,
      HttpServletRequest req)
      throws IOException {
    service.uploadPart(id, part, length, sha, req.getInputStream());
  }

  /**
   * 查询上传进度。
   *
   * @param id 上传任务 ID
   * @return 任务状态和已确认分片
   */
  @GetMapping("/{id}")
  UploadStatusView get(@PathVariable UUID id) {
    return service.get(id);
  }

  /**
   * 提交后台合并和校验请求。
   *
   * @param id 上传任务 ID
   * @return 已完成结果或 HTTP 202 处理中状态
   */
  @PostMapping("/{id}/complete")
  ResponseEntity<UploadStatusView> complete(@PathVariable UUID id) {
    UploadStatusView v = service.requestCompletion(id);
    return ResponseEntity.status(
            v.status() == UploadTaskStatus.COMPLETED ? HttpStatus.OK : HttpStatus.ACCEPTED)
        .body(v);
  }

  /**
   * 幂等取消上传任务。
   *
   * @param id 上传任务 ID
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(@PathVariable UUID id) {
    service.cancel(id);
  }

  /**
   * 分片上传初始化 HTTP 入参。
   *
   * @param originalName 原始文件名
   * @param size 文件总字节数
   * @param sha256 客户端完整摘要
   * @param contentType 客户端声明的内容类型
   * @param businessType 业务类型
   * @param businessId 业务标识
   */
  public record InitializeRequest(
      @NotBlank String originalName,
      @Positive long size,
      @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256,
      String contentType,
      String businessType,
      String businessId) {}
}
