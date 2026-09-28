package io.github.chansan.example;

import io.github.chansan.example.web.RequestIdFilter;
import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.spi.*;
import java.util.Map;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;

/** FileBridge 本地演示应用。 */
@SpringBootApplication
public class ExampleApplication {
  /**
   * 启动演示应用。
   *
   * @param args Spring Boot 启动参数
   */
  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
  }

  /**
   * 创建请求追踪 ID 过滤器。
   *
   * @return 请求追踪 ID 过滤器
   */
  @Bean
  RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }

  @Configuration
  @Profile("demo")
  static class DemoSecurity {
    /**
     * 创建演示用固定身份提供器。
     *
     * @return 演示身份提供器
     */
    @Bean
    CurrentActorProvider actor() {
      return () -> new Actor("demo-tenant", "demo-user", Map.of("mode", "demo"));
    }

    /**
     * 创建演示用同租户同所有者访问策略。
     *
     * @return 文件访问策略
     */
    @Bean
    FileAccessPolicy policy() {
      return new FileAccessPolicy() {
        /**
         * 校验资源属于当前演示身份。
         *
         * @param a 当前身份
         * @param t 资源租户 ID
         * @param o 资源所有者 ID
         */
        private void own(Actor a, String t, String o) {
          if (!a.tenantId().equals(t) || !a.ownerId().equals(o))
            throw new FileBridgeException(FileBridgeErrorCode.ACCESS_DENIED, "Resource not found");
        }

        /**
         * {@inheritDoc}
         *
         * @param a 当前身份
         * @param b 业务类型
         * @param i 业务标识
         */
        @Override
        public void checkUpload(Actor a, String b, String i) {}

        /**
         * {@inheritDoc}
         *
         * @param a 当前身份
         * @param r 业务文件引用
         */
        @Override
        public void checkRead(Actor a, FileReference r) {
          own(a, r.tenantId(), r.ownerId());
        }

        /**
         * {@inheritDoc}
         *
         * @param a 当前身份
         * @param r 业务文件引用
         */
        @Override
        public void checkDelete(Actor a, FileReference r) {
          own(a, r.tenantId(), r.ownerId());
        }

        /**
         * {@inheritDoc}
         *
         * @param a 当前身份
         * @param t 上传任务
         */
        @Override
        public void checkUploadTask(Actor a, UploadTask t) {
          own(a, t.tenantId(), t.ownerId());
        }

        /**
         * {@inheritDoc}
         *
         * @param a 当前身份
         * @param r 秒传候选引用
         * @return 租户和所有者完全匹配时返回 {@code true}
         */
        @Override
        public boolean canReuse(Actor a, FileReference r) {
          return a.tenantId().equals(r.tenantId()) && a.ownerId().equals(r.ownerId());
        }
      };
    }
  }
}
