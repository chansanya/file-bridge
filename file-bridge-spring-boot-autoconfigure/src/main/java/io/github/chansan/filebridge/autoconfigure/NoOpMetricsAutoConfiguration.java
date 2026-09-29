package io.github.chansan.filebridge.autoconfigure;

import io.github.chansan.filebridge.core.spi.FileBridgeMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

/** Micrometer 不存在时提供空指标实现。 */
@AutoConfiguration(before = FileBridgeAutoConfiguration.class)
public class NoOpMetricsAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  FileBridgeMetrics noOpFileBridgeMetrics() {
    return FileBridgeMetrics.noop();
  }
}
