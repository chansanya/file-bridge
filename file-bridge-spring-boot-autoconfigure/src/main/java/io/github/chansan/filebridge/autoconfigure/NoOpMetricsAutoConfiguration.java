package io.github.chansan.filebridge.autoconfigure;

import io.github.chansan.filebridge.core.spi.FileBridgeMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

/** 未提供自定义指标实现时注册空实现。 */
@AutoConfiguration(before = FileBridgeAutoConfiguration.class)
public class NoOpMetricsAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  FileBridgeMetrics noOpFileBridgeMetrics() {
    return FileBridgeMetrics.noop();
  }
}
