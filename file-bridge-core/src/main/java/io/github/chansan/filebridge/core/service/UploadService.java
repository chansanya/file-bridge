package io.github.chansan.filebridge.core.service;

import io.github.chansan.filebridge.core.model.UploadInitialization;
import io.github.chansan.filebridge.core.model.UploadPart;
import io.github.chansan.filebridge.core.model.UploadStatusView;
import java.io.InputStream;
import java.util.UUID;

/** 提供分片上传、断点续传和完成任务能力。 */
public interface UploadService {
  /**
   * 检查秒传候选并在未命中时创建分片任务。
   *
   * @param command 文件摘要、大小和业务信息
   * @return 秒传结果或新建上传任务的分片规则
   */
  UploadInitialization initialize(InitializeUploadCommand command);

  /**
   * 上传一个分片。
   *
   * @param uploadId 上传任务 ID
   * @param partNumber 从 1 开始的分片序号
   * @param contentLength 实际请求体长度
   * @param claimedPartSha256 客户端声明的分片 SHA-256，可为空
   * @param input 分片内容输入流
   * @return 服务端确认并持久化的分片记录
   */
  UploadPart uploadPart(
      UUID uploadId,
      int partNumber,
      Long contentLength,
      String claimedPartSha256,
      InputStream input);

  /**
   * 查询任务和已确认分片。
   *
   * @param uploadId 上传任务 ID
   * @return 上传任务当前状态
   */
  UploadStatusView get(UUID uploadId);

  /**
   * 请求后台合并并校验所有分片。
   *
   * @param uploadId 上传任务 ID
   * @return 请求提交后的任务状态
   */
  UploadStatusView requestCompletion(UUID uploadId);

  /**
   * 幂等取消尚未完成的上传任务。
   *
   * @param uploadId 上传任务 ID
   */
  void cancel(UUID uploadId);
}
