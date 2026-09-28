package io.github.chansan.filebridge.autoconfigure;

import io.github.chansan.filebridge.core.service.*;
import io.github.chansan.filebridge.web.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

/** FileBridge Servlet Web 自动配置。 */
@AutoConfiguration(after = FileBridgeAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "io.github.chansan.filebridge.web.FileController")
@ConditionalOnProperty(prefix = "file-bridge.web", name = "enabled", matchIfMissing = true)
public class FileBridgeWebAutoConfiguration {
  @Bean
  @ConditionalOnBean(FileService.class)
  @ConditionalOnMissingBean
  FileController fileController(FileService s) {
    return new FileController(s);
  }

  @Bean
  @ConditionalOnBean(UploadService.class)
  @ConditionalOnMissingBean
  UploadController uploadController(UploadService s) {
    return new UploadController(s);
  }

  @Bean
  @ConditionalOnMissingBean
  FileBridgeExceptionHandler fileBridgeExceptionHandler() {
    return new FileBridgeExceptionHandler();
  }

  @Bean
  @ConditionalOnMissingBean
  RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }
}
