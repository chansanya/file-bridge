package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.spi.*;
import java.io.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 通过数据库租约协调的上传完成工作器。 */
public final class UploadCompletionWorker implements AutoCloseable {
  private static final System.Logger LOGGER =
      System.getLogger(UploadCompletionWorker.class.getName());
  private final UploadRepository uploads;
  private final FileRepository files;
  private final StorageRegistry storages;
  private final TransactionRunner tx;
  private final FileBridgeMetrics metrics;
  private final String workerId;
  private static final int MAX_COMPLETION_ATTEMPTS = 3;
  private final Duration lease;
  private final ScheduledExecutorService heartbeatExecutor;

  /**
   * 创建上传完成工作器。
   *
   * @param uploads 上传任务仓储
   * @param files 文件仓储
   * @param storages 存储注册表
   * @param tx 事务执行器
   * @param workerId 工作器标识
   * @param lease 完成租约时长
   */
  public UploadCompletionWorker(
      UploadRepository uploads,
      FileRepository files,
      StorageRegistry storages,
      TransactionRunner tx,
      FileBridgeMetrics metrics,
      String workerId,
      Duration lease) {
    this.uploads = uploads;
    this.files = files;
    this.storages = storages;
    this.tx = tx;
    this.metrics = metrics;
    this.workerId = workerId;
    this.lease = lease;
    this.heartbeatExecutor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "file-bridge-lease-heartbeat");
              thread.setDaemon(true);
              return thread;
            });
  }

  /**
   * 执行一轮待完成上传任务处理。
   *
   * @param limit 单轮最大拉取数量
   * @return 成功完成的任务数量
   */
  public int runOnce(int limit) {
    int done = 0;
    for (UploadTask t : uploads.findCompletableOrExpiredLeases(Instant.now(), limit)) {
      if (process(t)) done++;
    }
    return done;
  }

  /**
   * 竞争租约并处理单个上传任务。
   *
   * @param snapshot 待处理任务快照
   * @return 当前工作器成功完成任务时返回 {@code true}
   */
  public boolean process(UploadTask snapshot) {
    long started = System.nanoTime();
    Instant now = Instant.now();
    if (!uploads.acquireCompletionLease(snapshot.id(), workerId, now.plus(lease), now)) {
      return false;
    }

    UploadTask task = uploads.findTask(snapshot.id()).orElseThrow();
    AtomicBoolean leaseLost = new AtomicBoolean(false);
    long heartbeatMillis = Math.max(1000, lease.toMillis() / 3);
    ScheduledFuture<?> heartbeat =
        heartbeatExecutor.scheduleAtFixedRate(
            () -> {
              Instant heartbeatNow = Instant.now();
              boolean renewed =
                  uploads.renewCompletionLease(
                      task.id(), workerId, heartbeatNow.plus(lease), heartbeatNow);
              if (!renewed) leaseLost.set(true);
            },
            heartbeatMillis,
            heartbeatMillis,
            TimeUnit.MILLISECONDS);

    try {
      StorageProvider storage = storages.require(task.storageId());
      if (!(storage instanceof MultipartStorageProvider multipart)) {
        throw new FileBridgeException(
            FileBridgeErrorCode.CAPABILITY_NOT_SUPPORTED,
            "Storage does not support multipart completion");
      }
      List<UploadedPart> parts =
          uploads.findCompletedParts(task.id()).stream()
              .sorted(Comparator.comparingInt(UploadPart::partNumber))
              .map(
                  part ->
                      new UploadedPart(
                          part.partNumber(), part.size(), part.sha256(), part.providerPartTag()))
              .toList();
      ObjectLocation target = storage.locate(task.objectKey());
      StoredObject stored =
          storage
              .stat(target)
              .orElseGet(
                  () ->
                      multipart.completeMultipart(
                          new MultipartUploadHandle(task.providerUploadId(), task.objectKey()),
                          parts,
                          task.contentType()));
      requireLease(leaseLost);
      if (!uploads.moveToVerifying(task.id(), workerId, Instant.now())) {
        throw new FileBridgeException(
            FileBridgeErrorCode.INVALID_UPLOAD_STATE, "Completion lease was lost");
      }

      Verified verified = verify(multipart, stored.location());
      requireLease(leaseLost);
      if (verified.size != task.expectedSize()
          || (task.claimedSha256() != null && !task.claimedSha256().equals(verified.sha))) {
        throw new FileBridgeException(
            FileBridgeErrorCode.CHECKSUM_MISMATCH,
            "Completed object does not match expected size or checksum");
      }

      Instant completed = Instant.now();
      UUID objectId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();
      StorageObjectRecord object =
          new StorageObjectRecord(
              objectId,
              stored.location(),
              verified.size,
              verified.sha,
              task.contentType(),
              StorageObjectStatus.AVAILABLE,
              completed,
              completed,
              completed,
              0);
      FileReference reference =
          new FileReference(
              fileId,
              objectId,
              task.tenantId(),
              task.ownerId(),
              task.originalName(),
              task.businessType(),
              task.businessId(),
              FileReferenceStatus.ACTIVE,
              completed,
              null);
      tx.required(
          () -> {
            files.insertObject(object);
            files.insertReference(reference);
            if (!uploads.complete(task.id(), workerId, fileId, completed)) {
              throw new FileBridgeException(
                  FileBridgeErrorCode.INVALID_UPLOAD_STATE, "Completion lease was lost");
            }
          });
      metrics.record("completion", "success", task.expectedSize(), System.nanoTime() - started);
      return true;
    } catch (RuntimeException error) {
      metrics.record("completion", "failure", task.expectedSize(), System.nanoTime() - started);
      LOGGER.log(System.Logger.Level.WARNING, "Upload completion failed: " + task.id(), error);
      Instant failedAt = Instant.now();
      String message =
          error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
      if (isPermanent(error)) {
        uploads.fail(task.id(), workerId, message, failedAt);
      } else {
        uploads.retryCompletion(
            task.id(),
            workerId,
            message,
            failedAt.plus(Duration.ofMinutes(1)),
            MAX_COMPLETION_ATTEMPTS,
            failedAt);
      }
      return false;
    } finally {
      heartbeat.cancel(false);
    }
  }

  private static void requireLease(AtomicBoolean leaseLost) {
    if (leaseLost.get()) {
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_UPLOAD_STATE, "Completion lease was lost");
    }
  }

  private static boolean isPermanent(RuntimeException error) {
    if (!(error instanceof FileBridgeException fileBridgeError)) return false;
    return switch (fileBridgeError.code()) {
      case CHECKSUM_MISMATCH,
          INVALID_PART,
          INVALID_REQUEST,
          INVALID_UPLOAD_STATE,
          CAPABILITY_NOT_SUPPORTED,
          CONFIGURATION_ERROR ->
          true;
      default -> false;
    };
  }

  /**
   * 流式读取最终对象并校验摘要。
   *
   * @param p 目标存储适配器
   * @param l 最终对象定位信息
   * @return 实际大小和 SHA-256
   */
  private static Verified verify(StorageProvider p, ObjectLocation l) {
    try (InputStream in = p.open(l)) {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      byte[] b = new byte[64 * 1024];
      long size = 0;
      int n;
      while ((n = in.read(b)) >= 0) {
        if (n > 0) {
          d.update(b, 0, n);
          size += n;
        }
      }
      return new Verified(size, HexFormat.of().formatHex(d.digest()));
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new FileBridgeException(
          FileBridgeErrorCode.STORAGE_FAILURE, "Unable to verify completed object", e);
    }
  }

  /**
   * 最终对象校验结果。
   *
   * @param size 实际字节数
   * @param sha 实际内容 SHA-256
   */
  @Override
  public void close() {
    heartbeatExecutor.shutdownNow();
  }

  private record Verified(long size, String sha) {}
}
