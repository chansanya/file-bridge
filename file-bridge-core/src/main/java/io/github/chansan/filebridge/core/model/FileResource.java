package io.github.chansan.filebridge.core.model;

import java.io.IOException;
import java.io.InputStream;

/**
 * 可关闭的文件下载资源。
 *
 * @param metadata 文件元数据
 * @param stream 文件内容输入流
 */
public final class FileResource implements AutoCloseable {
  private final FileMetadata metadata;
  private final InputStream stream;

  public FileResource(FileMetadata metadata, InputStream stream) {
    this.metadata = metadata;
    this.stream = stream;
  }

  public FileMetadata metadata() {
    return metadata;
  }

  public InputStream stream() {
    return stream;
  }

  public FileMetadata getMetadata() {
    return metadata;
  }

  public InputStream getStream() {
    return stream;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof FileResource)) return false;
    FileResource other = (FileResource) value;
    return java.util.Objects.equals(metadata, other.metadata)
        && java.util.Objects.equals(stream, other.stream);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(metadata, stream);
  }

  @Override
  public String toString() {
    return "FileResource{" + "metadata=" + metadata + ", stream=" + stream + "}";
  }

  /** 关闭文件内容流。 */
  @Override
  public void close() throws IOException {
    stream.close();
  }
}
