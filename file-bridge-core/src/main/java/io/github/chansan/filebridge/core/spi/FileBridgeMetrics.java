package io.github.chansan.filebridge.core.spi;

/** 可选可观测性端口；未配置 Micrometer 时使用空实现。 */
public interface FileBridgeMetrics {
  void record(String operation, String outcome, long amount, long durationNanos);

  static FileBridgeMetrics noop() {
    return (operation, outcome, amount, durationNanos) -> {};
  }
}
