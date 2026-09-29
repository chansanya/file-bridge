package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.error.*;
import java.io.*;

/** 在流式读取过程中强制限制最大字节数，避免未知长度上传写完整对象后才失败。 */
final class SizeLimitedInputStream extends FilterInputStream {
  private final long maximumBytes;
  private long count;

  SizeLimitedInputStream(InputStream input, long maximumBytes) {
    super(input);
    this.maximumBytes = maximumBytes;
  }

  @Override
  public int read() throws IOException {
    int value = super.read();
    if (value >= 0) increment(1);
    return value;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    int read = super.read(buffer, offset, length);
    if (read > 0) increment(read);
    return read;
  }

  private void increment(int read) {
    count += read;
    if (count > maximumBytes) {
      throw new FileBridgeException(
          FileBridgeErrorCode.FILE_TOO_LARGE, "File exceeds configured size limit");
    }
  }
}
