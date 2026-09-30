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
  private static final io.github.chansan.filebridge.core.util.BridgeLog.Logger LOGGER =
      io.github.chansan.filebridge.core.util.BridgeLog.getLogger(
          UploadCompletionWorker.class.getName());
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

    UploadTask task =
        uploads.findTask(snapshot.id()).orElseThrow(() -> new java.util.NoSuchElementException());
    LOGGER.log(
        io.github.chansan.filebridge.core.util.BridgeLog.Level.INFO,
        "Multipart completion started uploadId={0} storageId={1} bytes={2} workerId={3}",
        task.id(),
        task.storageId(),
        task.expectedSize(),
        workerId);
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
      if (!(storage instanceof MultipartStorageProvider)) {
        throw new FileBridgeException(
            FileBridgeErrorCode.CAPABILITY_NOT_SUPPORTED,
            "Storage does not support multipart completion");
      }
      MultipartStorageProvider multipart = (MultipartStorageProvider) storage;
      List<UploadedPart> parts =
          uploads.findCompletedParts(task.id()).stream()
              .sorted(Comparator.comparingInt(UploadPart::partNumber))
              .map(
                  part ->
                      new UploadedPart(
                          part.partNumber(), part.size(), part.sha256(), part.providerPartTag()))
              .collect(java.util.stream.Collectors.toList());
      LOGGER.log(
          io.github.chansan.filebridge.core.util.BridgeLog.Level.DEBUG,
          "Multipart completion parts loaded uploadId={0} completedParts={1}/{2}",
          task.id(),
          parts.size(),
          task.totalParts());
      ObjectLocation target = storage.locate(task.objectKey());
      Optional<StoredObject> existing = storage.stat(target);
      StoredObject stored;
      if (existing.isPresent()) {
        stored = existing.get();
        LOGGER.log(
            io.github.chansan.filebridge.core.util.BridgeLog.Level.INFO,
            "Multipart completed object recovered uploadId={0} storageId={1}",
            task.id(),
            task.storageId());
      } else {
        stored =
            multipart.completeMultipart(
                new MultipartUploadHandle(task.providerUploadId(), task.objectKey()),
                parts,
                task.contentType());
        LOGGER.log(
            io.github.chansan.filebridge.core.util.BridgeLog.Level.INFO,
            "Multipart parts merged uploadId={0} storageId={1} parts={2}",
            task.id(),
            task.storageId(),
            parts.size());
      }
      requireLease(leaseLost);
      if (!uploads.moveToVerifying(task.id(), workerId, Instant.now())) {
        throw new FileBridgeException(
            FileBridgeErrorCode.INVALID_UPLOAD_STATE, "Completion lease was lost");
      }

      LOGGER.log(
          io.github.chansan.filebridge.core.util.BridgeLog.Level.INFO,
          "Multipart verification started uploadId={0} storageId={1} expectedBytes={2}",
          task.id(),
          task.storageId(),
          task.expectedSize());
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
      long duration = System.nanoTime() - started;
      metrics.record("completion", "success", task.expectedSize(), duration);
      LOGGER.log(
          io.github.chansan.filebridge.core.util.BridgeLog.Level.INFO,
          "Multipart completion finished uploadId={0} fileId={1} storageId={2} bytes={3}"
              + " durationMs={4}",
          task.id(),
          fileId,
          task.storageId(),
          verified.size,
          Duration.ofNanos(duration).toMillis());
      return true;
    } catch (RuntimeException error) {
      long duration = System.nanoTime() - started;
      metrics.record("completion", "failure", task.expectedSize(), duration);
      Instant failedAt = Instant.now();
      String message =
          error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
      boolean permanent = isPermanent(error);
      if (permanent) {
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
      logFailure(task, duration, permanent, error);
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
    if (!(error instanceof FileBridgeException)) return false;
    FileBridgeException fileBridgeError = (FileBridgeException) error;
    switch (fileBridgeError.code()) {
      case CHECKSUM_MISMATCH:
      case INVALID_PART:
      case INVALID_REQUEST:
      case INVALID_UPLOAD_STATE:
      case CAPABILITY_NOT_SUPPORTED:
      case CONFIGURATION_ERROR:
        return true;
      default:
        return false;
    }
  }

  /**
   * 记录后台完成失败及后续处理动作。
   *
   * @param task 上传任务
   * @param duration 纳秒耗时
   * @param permanent 是否为永久失败
   * @param error 失败异常
   */
  private static void logFailure(
      UploadTask task, long duration, boolean permanent, RuntimeException error) {
    String message =
        "Multipart completion failed uploadId="
            + task.id()
            + " storageId="
            + task.storageId()
            + " bytes="
            + task.expectedSize()
            + " durationMs="
            + Duration.ofNanos(duration).toMillis()
            + " action="
            + (permanent ? "failed" : "retry-scheduled")
            + " errorCode="
            + errorCode(error);
    if (error instanceof FileBridgeException) {
      LOGGER.log(io.github.chansan.filebridge.core.util.BridgeLog.Level.WARNING, message);
    } else {
      LOGGER.log(io.github.chansan.filebridge.core.util.BridgeLog.Level.ERROR, message, error);
    }
  }

  /**
   * 返回稳定业务错误码，未知异常退化为异常类型名。
   *
   * @param error 失败异常
   * @return 错误码或异常类型名
   */
  private static String errorCode(RuntimeException error) {
    if (error instanceof FileBridgeException) {
      return ((FileBridgeException) error).code().name();
    }
    return error.getClass().getSimpleName();
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
      return new Verified(size, io.github.chansan.filebridge.core.util.HexUtils.toHex(d.digest()));
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

  private static final class Verified {
    private final long size;
    private final String sha;

    private Verified(long size, String sha) {
      this.size = size;
      this.sha = sha;
    }

    private long size() {
      return size;
    }

    private String sha() {
      return sha;
    }
  }
}
