package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.service.CleanupService;
import io.github.chansan.filebridge.core.spi.*;
import java.time.*;

/** 过期任务和无引用对象清理服务。 */
public final class DefaultCleanupService implements CleanupService {
  private final UploadRepository uploads;
  private final FileRepository files;
  private final StorageRegistry storages;
  private final Duration retention;

  public DefaultCleanupService(
      UploadRepository uploads,
      FileRepository files,
      StorageRegistry storages,
      Duration retention) {
    this.uploads = uploads;
    this.files = files;
    this.storages = storages;
    this.retention = retention;
  }

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
        files.markObjectError(o.id(), e.getMessage(), Instant.now());
      }
    }
    return count;
  }
}
