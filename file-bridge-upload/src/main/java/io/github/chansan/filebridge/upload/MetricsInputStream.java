package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.spi.FileBridgeMetrics;
import java.io.*;

/** 在下载流关闭时记录实际读取字节数和持续时间。 */
final class MetricsInputStream extends FilterInputStream {
  private final FileBridgeMetrics metrics;
  private final long expectedSize;
  private final long started = System.nanoTime();
  private long count;
  private boolean closed;

  MetricsInputStream(InputStream input, long expectedSize, FileBridgeMetrics metrics) {
    super(input);
    this.expectedSize = expectedSize;
    this.metrics = metrics;
  }

  @Override
  public int read() throws IOException {
    int value = super.read();
    if (value >= 0) count++;
    return value;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    int read = super.read(buffer, offset, length);
    if (read > 0) count += read;
    return read;
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    try {
      super.close();
      String outcome = count == expectedSize ? "success" : "incomplete";
      metrics.record("download", outcome, count, System.nanoTime() - started);
    } catch (IOException error) {
      metrics.record("download", "failure", count, System.nanoTime() - started);
      throw error;
    }
  }
}
