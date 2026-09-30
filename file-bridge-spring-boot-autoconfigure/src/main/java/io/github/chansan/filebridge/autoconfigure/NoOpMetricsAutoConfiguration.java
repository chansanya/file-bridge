package io.github.chansan.filebridge.autoconfigure;

import io.github.chansan.filebridge.core.spi.FileBridgeMetrics;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 未提供自定义指标实现时注册空实现。 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(FileBridgeAutoConfiguration.class)
public class NoOpMetricsAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  FileBridgeMetrics noOpFileBridgeMetrics() {
    return FileBridgeMetrics.noop();
  }
}
