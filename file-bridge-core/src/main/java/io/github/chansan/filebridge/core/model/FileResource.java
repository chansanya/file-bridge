package io.github.chansan.filebridge.core.model;

import java.io.IOException;
import java.io.InputStream;

/**
 * 可关闭的文件下载资源。
 *
 * @param metadata 文件元数据
 * @param stream 文件内容输入流
 */
public record FileResource(FileMetadata metadata, InputStream stream) implements AutoCloseable {
  @Override
  public void close() throws IOException {
    stream.close();
  }
}
