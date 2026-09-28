package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.spi.*;
import java.io.*;
import java.security.*;
import java.time.*;
import java.util.*;

/** 通过数据库租约协调的上传完成工作器。 */
public final class UploadCompletionWorker {
  private final UploadRepository uploads;
  private final FileRepository files;
  private final StorageRegistry storages;
  private final TransactionRunner tx;
  private final String workerId;
  private final Duration lease;

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
      String workerId,
      Duration lease) {
    this.uploads = uploads;
    this.files = files;
    this.storages = storages;
    this.tx = tx;
    this.workerId = workerId;
    this.lease = lease;
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
    Instant now = Instant.now();
    // 数据库条件更新是多实例之间唯一的完成权仲裁，不能依赖进程内锁。
    if (!uploads.acquireCompletionLease(snapshot.id(), workerId, now.plus(lease), now))
      return false;
    UploadTask t = uploads.findTask(snapshot.id()).orElseThrow();
    try {
      MultipartStorageProvider p = (MultipartStorageProvider) storages.require(t.storageId());
      List<UploadPart> db = uploads.findCompletedParts(t.id());
      List<UploadedPart> parts =
          db.stream()
              .sorted(Comparator.comparingInt(UploadPart::partNumber))
              .map(v -> new UploadedPart(v.partNumber(), v.size(), v.sha256(), v.providerPartTag()))
              .toList();
      // 平台完成分片后必须重新读取最终对象，不能拼接分片摘要冒充整文件摘要。
      StoredObject stored =
          p.completeMultipart(
              new MultipartUploadHandle(t.providerUploadId(), t.objectKey()),
              parts,
              t.contentType());
      uploads.moveToVerifying(t.id(), workerId, Instant.now());
      Verified v = verify(p, stored.location());
      if (v.size != t.expectedSize()
          || (t.claimedSha256() != null && !t.claimedSha256().equals(v.sha)))
        throw new FileBridgeException(
            FileBridgeErrorCode.CHECKSUM_MISMATCH,
            "Completed object does not match expected size or checksum");
      Instant completed = Instant.now();
      UUID objectId = UUID.randomUUID(), fileId = UUID.randomUUID();
      StorageObjectRecord object =
          new StorageObjectRecord(
              objectId,
              stored.location(),
              v.size,
              v.sha,
              t.contentType(),
              StorageObjectStatus.AVAILABLE,
              completed,
              completed,
              completed,
              0);
      FileReference ref =
          new FileReference(
              fileId,
              objectId,
              t.tenantId(),
              t.ownerId(),
              t.originalName(),
              t.businessType(),
              t.businessId(),
              FileReferenceStatus.ACTIVE,
              completed,
              null);
      // 发布对象、创建业务引用和写入唯一完成结果必须处于同一事务。
      tx.required(
          () -> {
            files.insertObject(object);
            files.insertReference(ref);
            if (!uploads.complete(t.id(), workerId, fileId, completed))
              throw new FileBridgeException(
                  FileBridgeErrorCode.INVALID_UPLOAD_STATE, "Completion lease was lost");
          });
      return true;
    } catch (RuntimeException e) {
      uploads.fail(t.id(), workerId, Instant.now());
      return false;
    }
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
  private record Verified(long size, String sha) {}
}
