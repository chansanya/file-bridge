package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.service.CleanupService;
import io.github.chansan.filebridge.core.spi.*;
import java.time.*;

/** 过期任务和无引用对象清理服务。 */
public final class DefaultCleanupService implements CleanupService {
  private static final System.Logger LOGGER =
      System.getLogger(DefaultCleanupService.class.getName());
  private final UploadRepository uploads;
  private final FileRepository files;
  private final StorageRegistry storages;
  private final ReconciliationRepository issues;
  private final Duration retention;

  /**
   * 创建默认清理服务。
   *
   * @param uploads 上传任务仓储
   * @param files 文件仓储
   * @param storages 存储注册表
   * @param retention 无引用对象保留时长
   */
  public DefaultCleanupService(
      UploadRepository uploads,
      FileRepository files,
      StorageRegistry storages,
      ReconciliationRepository issues,
      Duration retention) {
    this.uploads = uploads;
    this.files = files;
    this.storages = storages;
    this.issues = issues;
    this.retention = retention;
  }

  /**
   * {@inheritDoc}
   *
   * @param limit 单轮最大处理数量
   * @return 实际清理的任务数
   */
  @Override
  public int cleanExpiredUploads(int limit) {
    int count = 0;
    for (UploadTask t : uploads.findExpiredTasks(Instant.now(), limit)) {
      if (uploads.cancel(t.id(), Instant.now())) {
        StorageProvider p = storages.require(t.storageId());
        if (p instanceof MultipartStorageProvider m)
          m.abortMultipart(new MultipartUploadHandle(t.providerUploadId(), t.objectKey()));
        count++;
      }
    }
    return count;
  }

  /**
   * {@inheritDoc}
   *
   * @param limit 单轮最大处理数量
   * @return 实际删除的对象数
   */
  @Override
  public int cleanUnreferencedObjects(int limit) {
    int count = 0;
    for (StorageObjectRecord o :
        files.findUnreferencedAvailable(Instant.now().minus(retention), limit)) {
      if (!files.markObjectDeletePending(o.id(), Instant.now())) continue;
      try {
        storages.require(o.location().storageId()).delete(o.location());
        files.markObjectDeleted(o.id(), Instant.now());
        count++;
      } catch (RuntimeException e) {
        LOGGER.log(System.Logger.Level.WARNING, "Object cleanup failed: " + o.id(), e);
        Instant failedAt = Instant.now();
        issues.upsert(
            "OBJECT_DELETE_FAILED:" + o.id(),
            "OBJECT_DELETE_FAILED",
            o.location().storageId(),
            o.location().objectKey(),
            o.id().toString(),
            e.getMessage(),
            failedAt.plus(Duration.ofMinutes(5)),
            failedAt);
      }
    }
    return count;
  }
}
