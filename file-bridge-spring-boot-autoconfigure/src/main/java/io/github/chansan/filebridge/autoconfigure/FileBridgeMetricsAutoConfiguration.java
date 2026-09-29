package io.github.chansan.filebridge.autoconfigure;

import io.github.chansan.filebridge.core.spi.FileBridgeMetrics;
import io.micrometer.core.instrument.*;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

/** Micrometer 存在时注册 FileBridge 指标实现。 */
@AutoConfiguration(before = FileBridgeAutoConfiguration.class)
public class FileBridgeMetricsAutoConfiguration {
  @Bean
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean
  FileBridgeMetrics micrometerFileBridgeMetrics(MeterRegistry registry) {
    return (operation, outcome, amount, durationNanos) -> {
      Counter.builder("filebridge.operations")
          .tag("operation", operation)
          .tag("outcome", outcome)
          .register(registry)
          .increment();
      DistributionSummary.builder("filebridge.amount")
          .tag("operation", operation)
          .tag("outcome", outcome)
          .register(registry)
          .record(amount);
      if (durationNanos > 0) {
        Timer.builder("filebridge.duration")
            .tag("operation", operation)
            .tag("outcome", outcome)
            .register(registry)
            .record(Duration.ofNanos(durationNanos));
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean({FileBridgeMetrics.class, MeterRegistry.class})
  FileBridgeMetrics noOpFileBridgeMetrics() {
    return FileBridgeMetrics.noop();
  }
}
