package io.github.chansan.filebridge.example;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.spi.*;
import java.util.Map;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;

/** FileBridge 本地演示应用。 */
@SpringBootApplication
public class FileBridgeExampleApplication {
  public static void main(String[] args) {
    SpringApplication.run(FileBridgeExampleApplication.class, args);
  }

  @Configuration
  @Profile("demo")
  static class DemoSecurity {
    @Bean
    CurrentActorProvider actor() {
      return () -> new Actor("demo-tenant", "demo-user", Map.of("mode", "demo"));
    }

    @Bean
    FileAccessPolicy policy() {
      return new FileAccessPolicy() {
        private void own(Actor a, String t, String o) {
          if (!a.tenantId().equals(t) || !a.ownerId().equals(o))
            throw new FileBridgeException(FileBridgeErrorCode.ACCESS_DENIED, "Resource not found");
        }

        public void checkUpload(Actor a, String b, String i) {}

        public void checkRead(Actor a, FileReference r) {
          own(a, r.tenantId(), r.ownerId());
        }

        public void checkDelete(Actor a, FileReference r) {
          own(a, r.tenantId(), r.ownerId());
        }

        public void checkUploadTask(Actor a, UploadTask t) {
          own(a, t.tenantId(), t.ownerId());
        }

        public boolean canReuse(Actor a, FileReference r) {
          return a.tenantId().equals(r.tenantId()) && a.ownerId().equals(r.ownerId());
        }
      };
    }
  }
}
