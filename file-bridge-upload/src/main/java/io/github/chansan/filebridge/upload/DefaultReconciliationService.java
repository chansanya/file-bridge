package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.FileRepository;
import io.github.chansan.filebridge.core.service.ReconciliationService;
import io.github.chansan.filebridge.core.spi.StorageRegistry;
import java.time.Instant;

/** 数据库与物理存储基础对账服务。 */
public final class DefaultReconciliationService implements ReconciliationService {
  private final FileRepository files;
  private final StorageRegistry storages;

  /**
   * 创建默认对账服务。
   *
   * @param files 文件仓储
   * @param storages 存储注册表
   */
  public DefaultReconciliationService(FileRepository files, StorageRegistry storages) {
    this.files = files;
    this.storages = storages;
  }

  /**
   * {@inheritDoc}
   *
   * @param limit 单次最多检查的对象数
   * @return 本次发现的问题数
   */
  @Override
  public int reconcile(int limit) {
    int issues = 0;
    for (StorageObjectRecord o : files.findObjectsByStatus(StorageObjectStatus.AVAILABLE, limit)) {
      try {
        if (storages.require(o.location().storageId()).stat(o.location()).isEmpty()) {
          files.markObjectError(o.id(), "Physical object is missing", Instant.now());
          issues++;
        }
      } catch (RuntimeException e) {
        issues++;
      }
    }
    return issues;
  }
}
