package io.github.chansan.filebridge.core.spi;

import io.github.chansan.filebridge.core.model.StoredObject;
import java.io.InputStream;
import java.util.List;

/** 支持平台原生分片或等价分片语义的存储实现。 */
public interface MultipartStorageProvider extends StorageProvider {
  /**
   * 初始化分片任务。
   *
   * @param objectKey 服务端生成的目标对象路径
   * @param contentType 文件内容类型
   * @return 平台上传 ID 与目标对象路径
   */
  MultipartUploadHandle initiateMultipart(String objectKey, String contentType);

  /**
   * 上传并确认单个分片。
   *
   * @param handle 平台分片任务句柄
   * @param partNumber 从 1 开始的分片序号
   * @param contentLength 分片字节数
   * @param input 分片内容输入流
   * @return 平台确认的分片信息
   */
  UploadedPart uploadPart(
      MultipartUploadHandle handle, int partNumber, long contentLength, InputStream input);

  /**
   * 查询平台已经确认的分片。
   *
   * @param handle 平台分片任务句柄
   * @return 按平台返回的已上传分片
   */
  List<UploadedPart> listParts(MultipartUploadHandle handle);

  /**
   * 完成分片任务并生成最终对象。
   *
   * @param handle 平台分片任务句柄
   * @param parts 按序提交的平台分片标签
   * @param contentType 最终对象内容类型
   * @return 已生成的最终对象元数据
   */
  StoredObject completeMultipart(
      MultipartUploadHandle handle, List<UploadedPart> parts, String contentType);

  /**
   * 幂等取消分片任务并清理临时数据。
   *
   * @param handle 平台分片任务句柄
   */
  void abortMultipart(MultipartUploadHandle handle);
}
