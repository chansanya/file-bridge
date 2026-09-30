package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.spi.FileBridgeMetrics;
import java.io.*;
import java.time.Duration;
import java.util.UUID;

/** 在下载流关闭时记录实际读取字节数、持续时间和结果。 */
final class MetricsInputStream extends FilterInputStream {
  private static final io.github.chansan.filebridge.core.util.BridgeLog.Logger LOGGER =
      io.github.chansan.filebridge.core.util.BridgeLog.getLogger(
          MetricsInputStream.class.getName());
  private final FileBridgeMetrics metrics;
  private final UUID fileId;
  private final String storageId;
  private final long expectedSize;
  private final long started = System.nanoTime();
  private long count;
  private boolean closed;
  private IOException readFailure;

  /**
   * 创建带下载指标和完成日志的输入流。
   *
   * @param input 底层输入流
   * @param fileId 业务文件 ID
   * @param storageId 存储实例 ID
   * @param expectedSize 预期文件字节数
   * @param metrics 指标端口
   */
  MetricsInputStream(
      InputStream input,
      UUID fileId,
      String storageId,
      long expectedSize,
      FileBridgeMetrics metrics) {
    super(input);
    this.fileId = fileId;
    this.storageId = storageId;
    this.expectedSize = expectedSize;
    this.metrics = metrics;
  }

  /** {@inheritDoc} */
  @Override
  public int read() throws IOException {
    try {
      int value = super.read();
      if (value >= 0) count++;
      return value;
    } catch (IOException error) {
      readFailure = error;
      throw error;
    }
  }

  /** {@inheritDoc} */
  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    try {
      int read = super.read(buffer, offset, length);
      if (read > 0) count += read;
      return read;
    } catch (IOException error) {
      readFailure = error;
      throw error;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    IOException closeFailure = null;
    try {
      super.close();
    } catch (IOException error) {
      closeFailure = error;
    }
    long duration = System.nanoTime() - started;
    IOException failure = readFailure != null ? readFailure : closeFailure;
    if (failure != null) {
      metrics.record("download", "failure", count, duration);
      LOGGER.log(
          io.github.chansan.filebridge.core.util.BridgeLog.Level.WARNING,
          "File download failed fileId="
              + fileId
              + " storageId="
              + storageId
              + " bytes="
              + count
              + " expectedBytes="
              + expectedSize
              + " durationMs="
              + Duration.ofNanos(duration).toMillis(),
          failure);
      if (closeFailure != null) throw closeFailure;
      return;
    }
    if (count == expectedSize) {
      metrics.record("download", "success", count, duration);
      LOGGER.log(
          io.github.chansan.filebridge.core.util.BridgeLog.Level.INFO,
          "File download completed fileId={0} storageId={1} bytes={2} durationMs={3}",
          fileId,
          storageId,
          count,
          Duration.ofNanos(duration).toMillis());
    } else {
      metrics.record("download", "incomplete", count, duration);
      LOGGER.log(
          io.github.chansan.filebridge.core.util.BridgeLog.Level.DEBUG,
          "File download stream closed fileId={0} storageId={1} bytes={2} expectedBytes={3}"
              + " durationMs={4}",
          fileId,
          storageId,
          count,
          expectedSize,
          Duration.ofNanos(duration).toMillis());
    }
  }
}
