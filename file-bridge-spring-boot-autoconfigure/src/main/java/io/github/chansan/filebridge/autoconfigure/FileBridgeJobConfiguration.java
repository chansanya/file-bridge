package io.github.chansan.filebridge.autoconfigure;

import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.service.*;
import io.github.chansan.filebridge.core.spi.*;
import io.github.chansan.filebridge.upload.UploadCompletionWorker;
import java.util.UUID;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.*;

/** 上传完成、清理和对账任务自动配置。 */
@AutoConfiguration(after = FileBridgeAutoConfiguration.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "file-bridge", name = "enabled", matchIfMissing = true)
public class FileBridgeJobConfiguration {
  /**
   * 创建上传完成工作器。
   *
   * @param u 上传任务仓储
   * @param f 文件仓储
   * @param s 存储注册表
   * @param t 事务执行器
   * @param p FileBridge 配置属性
   * @return 上传完成工作器
   */
  @Bean
  @ConditionalOnBean({
    UploadRepository.class,
    FileRepository.class,
    StorageRegistry.class,
    TransactionRunner.class
  })
  UploadCompletionWorker uploadCompletionWorker(
      UploadRepository u,
      FileRepository f,
      StorageRegistry s,
      TransactionRunner t,
      FileBridgeProperties p) {
    return new UploadCompletionWorker(
        u, f, s, t, UUID.randomUUID().toString(), p.getUpload().getLeaseDuration());
  }

  /**
   * 创建后台任务运行器。
   *
   * @param w 上传完成工作器，可为空
   * @param c 清理服务，可为空
   * @param r 对账服务，可为空
   * @return 后台任务运行器
   */
  @Bean
  JobRunner fileBridgeJobRunner(
      org.springframework.beans.factory.ObjectProvider<UploadCompletionWorker> w,
      org.springframework.beans.factory.ObjectProvider<CleanupService> c,
      org.springframework.beans.factory.ObjectProvider<ReconciliationService> r) {
    return new JobRunner(w.getIfAvailable(), c.getIfAvailable(), r.getIfAvailable());
  }

  public static final class JobRunner {
    private final UploadCompletionWorker worker;
    private final CleanupService cleanup;
    private final ReconciliationService reconciliation;

    /**
     * 创建后台任务运行器。
     *
     * @param w 上传完成工作器，可为空
     * @param c 清理服务，可为空
     * @param r 对账服务，可为空
     */
    JobRunner(UploadCompletionWorker w, CleanupService c, ReconciliationService r) {
      worker = w;
      cleanup = c;
      reconciliation = r;
    }

    /** 执行一轮上传完成处理。 */
    @Scheduled(fixedDelayString = "${file-bridge.worker.interval:5s}")
    public void complete() {
      if (worker != null) worker.runOnce(10);
    }

    /** 执行一轮过期任务和无引用对象清理。 */
    @Scheduled(fixedDelayString = "${file-bridge.cleanup.interval:30m}")
    public void clean() {
      if (cleanup != null) {
        cleanup.cleanExpiredUploads(100);
        cleanup.cleanUnreferencedObjects(100);
      }
    }

    /** 执行一轮对象状态对账。 */
    @Scheduled(fixedDelayString = "${file-bridge.reconciliation.interval:15m}")
    public void reconcile() {
      if (reconciliation != null) reconciliation.reconcile(100);
    }
  }
}
