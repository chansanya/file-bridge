package io.github.chansan.filebridge.core.service;

import io.github.chansan.filebridge.core.model.FileMetadata;
import io.github.chansan.filebridge.core.model.FileResource;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/** 提供普通文件上传、读取、下载和删除能力。 */
public interface FileService {
  /**
   * 以流式方式上传文件。
   *
   * @param command 上传元数据和幂等信息
   * @param input 文件内容输入流；调用方负责提供可读取的流
   * @return 已创建的业务文件元数据
   */
  FileMetadata upload(UploadFileCommand command, InputStream input);

  /**
   * 查询文件元数据。
   *
   * @param fileId 对外业务文件 ID
   * @return 当前身份有权访问的文件元数据
   */
  FileMetadata get(UUID fileId);

  /**
   * 打开文件下载流。
   *
   * @param fileId 对外业务文件 ID
   * @return 同时包含元数据和输入流的文件资源，使用后必须关闭
   */
  FileResource download(UUID fileId);

  /**
   * 创建短期下载地址。
   *
   * @param fileId 对外业务文件 ID
   * @param validity 地址有效时长
   * @return 存储服务生成的临时访问地址
   */
  URI createAccessUrl(UUID fileId, Duration validity);

  /**
   * 删除当前业务文件引用，不直接删除仍被其他引用使用的物理对象。
   *
   * @param fileId 对外业务文件 ID
   */
  void delete(UUID fileId);
}
