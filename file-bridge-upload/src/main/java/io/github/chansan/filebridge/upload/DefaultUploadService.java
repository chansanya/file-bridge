package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.service.*;
import io.github.chansan.filebridge.core.spi.*;
import java.io.InputStream;
import java.time.*;
import java.util.*;

/** 分片上传、断点续传和秒传的默认实现。 */
public final class DefaultUploadService implements UploadService {
  private final UploadRepository uploads;
  private final FileRepository files;
  private final IdempotencyRepository idempotency;
  private final TransactionRunner tx;
  private final StorageRegistry storages;
  private final String defaultStorage;
  private final CurrentActorProvider actors;
  private final FileAccessPolicy policy;
  private final UploadQuotaPolicy quota;
  private final ObjectKeyGenerator keys;
  private final DeduplicationScope deduplicationScope;
  private final long preferredPartSize;
  private final Duration taskTtl;
  private final Duration leaseDuration;
  private final String workerId;

  public DefaultUploadService(
      UploadRepository uploads,
      FileRepository files,
      IdempotencyRepository idempotency,
      TransactionRunner tx,
      StorageRegistry storages,
      String defaultStorage,
      CurrentActorProvider actors,
      FileAccessPolicy policy,
      UploadQuotaPolicy quota,
      ObjectKeyGenerator keys,
      DeduplicationScope scope,
      long preferredPartSize,
      Duration taskTtl,
      Duration leaseDuration,
      String workerId) {
    this.uploads = uploads;
    this.files = files;
    this.idempotency = idempotency;
    this.tx = tx;
    this.storages = storages;
    this.defaultStorage = defaultStorage;
    this.actors = actors;
    this.policy = policy;
    this.quota = quota;
    this.keys = keys;
    this.deduplicationScope = scope;
    this.preferredPartSize = preferredPartSize;
    this.taskTtl = taskTtl;
    this.leaseDuration = leaseDuration;
    this.workerId = workerId;
  }

  @Override
  public UploadInitialization initialize(InitializeUploadCommand c) {
    // 初始化阶段统一完成身份、权限、配额和幂等校验。
    Actor actor = actors.currentActor();
    policy.checkUpload(actor, c.businessType(), c.businessId());
    quota.check(actor, c.size());
    if (c.size() <= 0)
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_REQUEST, "Multipart upload requires a non-empty file");
    String requestHash = fingerprint(c);
    if (hasText(c.idempotencyKey())) {
      Optional<String> prior =
          idempotency.findResponse(
              actor.tenantId(), actor.ownerId(), "UPLOAD_INIT", c.idempotencyKey(), requestHash);
      if (prior.isPresent()) return decode(prior.get());
    }
    // 客户端摘要只用于候选匹配，仍需同时满足授权范围和对象可用状态。
    if (deduplicationScope != DeduplicationScope.DISABLED && hasText(c.sha256())) {
      Optional<FileReference> candidate =
          files.findReusable(
              actor.tenantId(),
              actor.ownerId(),
              c.size(),
              normalizeSha(c.sha256()),
              deduplicationScope);
      if (candidate.isPresent() && policy.canReuse(actor, candidate.get())) {
        FileReference source = candidate.get();
        FileReference copy =
            new FileReference(
                UUID.randomUUID(),
                source.objectId(),
                actor.tenantId(),
                actor.ownerId(),
                c.originalName(),
                c.businessType(),
                c.businessId(),
                FileReferenceStatus.ACTIVE,
                Instant.now(),
                null);
        UUID fileId =
            tx.required(
                () -> {
                  files.insertReference(copy);
                  if (hasText(c.idempotencyKey()))
                    idempotency.save(
                        actor.tenantId(),
                        actor.ownerId(),
                        "UPLOAD_INIT",
                        c.idempotencyKey(),
                        requestHash,
                        "INSTANT:" + copy.id(),
                        Instant.now().plus(Duration.ofHours(24)));
                  return copy.id();
                });
        return UploadInitialization.instant(fileId);
      }
    }
    StorageProvider provider = storages.require(defaultStorage);
    if (!(provider instanceof MultipartStorageProvider multipart))
      throw new FileBridgeException(
          FileBridgeErrorCode.CAPABILITY_NOT_SUPPORTED,
          "Storage does not support multipart uploads");
    // 分片规则取配置偏好与平台限制的交集，不能把本地默认值硬塞给云平台。
    StorageCapabilities caps = provider.capabilities();
    long partSize = Math.max(preferredPartSize, caps.minimumPartSize());
    partSize = Math.min(partSize, caps.maximumPartSize());
    int total = Math.toIntExact((c.size() + partSize - 1) / partSize);
    if (total > caps.maximumParts())
      throw new FileBridgeException(
          FileBridgeErrorCode.FILE_TOO_LARGE, "File requires too many parts");
    String key = keys.generate(actor, c.originalName());
    MultipartUploadHandle handle = multipart.initiateMultipart(key, c.contentType());
    Instant now = Instant.now();
    UUID id = UUID.randomUUID();
    UploadTask task =
        new UploadTask(
            id,
            actor.tenantId(),
            actor.ownerId(),
            defaultStorage,
            key,
            c.originalName(),
            c.businessType(),
            c.businessId(),
            c.contentType(),
            c.size(),
            normalizeNullableSha(c.sha256()),
            partSize,
            total,
            UploadTaskStatus.CREATED,
            handle.providerUploadId(),
            null,
            now.plus(taskTtl),
            null,
            null,
            0,
            now,
            now);
    try {
      tx.required(
          () -> {
            uploads.insertTask(task);
            if (hasText(c.idempotencyKey()))
              idempotency.save(
                  actor.tenantId(),
                  actor.ownerId(),
                  "UPLOAD_INIT",
                  c.idempotencyKey(),
                  requestHash,
                  "UPLOAD:" + id,
                  now.plus(Duration.ofHours(24)));
          });
    } catch (RuntimeException e) {
      try {
        multipart.abortMultipart(handle);
      } catch (RuntimeException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw e;
    }
    return UploadInitialization.upload(id, partSize, total, task.expiresAt());
  }

  @Override
  public UploadPart uploadPart(
      UUID id, int number, Long length, String claimedSha, InputStream input) {
    UploadTask task = requireAccessible(id);
    ensureActive(task);
    validatePart(task, number, length);
    MultipartStorageProvider p = asMultipart(task);
    // 只有存储平台确认成功后，才把分片记录为 COMPLETED。
    UploadedPart stored =
        p.uploadPart(
            new MultipartUploadHandle(task.providerUploadId(), task.objectKey()),
            number,
            length,
            input);
    String normalized = normalizeNullableSha(claimedSha);
    if (normalized != null && !normalized.equals(stored.sha256()))
      throw new FileBridgeException(
          FileBridgeErrorCode.CHECKSUM_MISMATCH, "Part checksum mismatch");
    Instant now = Instant.now();
    UploadPart part =
        new UploadPart(
            UUID.randomUUID(),
            id,
            number,
            stored.size(),
            stored.sha256(),
            stored.providerPartTag(),
            UploadPartStatus.COMPLETED,
            now,
            now);
    UploadPart saved = uploads.saveCompletedPart(part);
    if (task.status() == UploadTaskStatus.CREATED) uploads.markUploading(id, task.version(), now);
    return saved;
  }

  @Override
  public UploadStatusView get(UUID id) {
    UploadTask t = requireAccessible(id);
    return view(t);
  }

  @Override
  public UploadStatusView requestCompletion(UUID id) {
    UploadTask t = requireAccessible(id);
    if (t.status() == UploadTaskStatus.COMPLETED) return view(t);
    ensureActive(t);
    List<UploadPart> parts = uploads.findCompletedParts(id);
    if (parts.size() != t.totalParts())
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_UPLOAD_STATE, "Not all parts are uploaded");
    Instant requestedAt = Instant.now();
    // 使用立即过期的占位租约提交后台任务，实际工作器随后竞争正式租约。
    uploads.acquireCompletionLease(id, workerId, requestedAt, requestedAt);
    return view(uploads.findTask(id).orElse(t));
  }

  @Override
  public void cancel(UUID id) {
    UploadTask t = requireAccessible(id);
    if (t.status() == UploadTaskStatus.COMPLETED) return;
    if (uploads.cancel(id, Instant.now()))
      asMultipart(t).abortMultipart(new MultipartUploadHandle(t.providerUploadId(), t.objectKey()));
  }

  private UploadTask requireAccessible(UUID id) {
    UploadTask t =
        uploads
            .findTask(id)
            .orElseThrow(
                () ->
                    new FileBridgeException(
                        FileBridgeErrorCode.UPLOAD_NOT_FOUND, "Upload not found"));
    policy.checkUploadTask(actors.currentActor(), t);
    return t;
  }

  private void ensureActive(UploadTask t) {
    if (t.expiresAt().isBefore(Instant.now()))
      throw new FileBridgeException(FileBridgeErrorCode.UPLOAD_EXPIRED, "Upload expired");
    if (t.status() != UploadTaskStatus.CREATED && t.status() != UploadTaskStatus.UPLOADING)
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_UPLOAD_STATE,
          "Operation is not allowed in state " + t.status());
  }

  private MultipartStorageProvider asMultipart(UploadTask t) {
    StorageProvider p = storages.require(t.storageId());
    if (p instanceof MultipartStorageProvider m) return m;
    throw new FileBridgeException(
        FileBridgeErrorCode.CAPABILITY_NOT_SUPPORTED, "Storage does not support multipart uploads");
  }

  private static void validatePart(UploadTask t, int n, Long len) {
    if (n < 1 || n > t.totalParts())
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_PART, "Part number is out of range");
    long expected =
        n == t.totalParts() ? t.expectedSize() - t.partSize() * (t.totalParts() - 1) : t.partSize();
    if (len == null || len != expected)
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_PART, "Part length does not match expected length");
  }

  private UploadStatusView view(UploadTask t) {
    return new UploadStatusView(
        t.id(),
        t.status(),
        t.expectedSize(),
        t.partSize(),
        t.totalParts(),
        uploads.findCompletedParts(t.id()).stream().map(UploadPart::partNumber).toList(),
        t.resultFileId(),
        t.expiresAt());
  }

  private UploadInitialization decode(String value) {
    String[] p = value.split(":", 2);
    UUID id = UUID.fromString(p[1]);
    if ("INSTANT".equals(p[0])) return UploadInitialization.instant(id);
    UploadTask t =
        uploads
            .findTask(id)
            .orElseThrow(
                () ->
                    new FileBridgeException(
                        FileBridgeErrorCode.UPLOAD_NOT_FOUND,
                        "Idempotent upload task no longer exists"));
    return UploadInitialization.upload(t.id(), t.partSize(), t.totalParts(), t.expiresAt());
  }

  private static String fingerprint(InitializeUploadCommand c) {
    String value =
        String.join(
            "\n",
            Objects.toString(c.originalName(), ""),
            Long.toString(c.size()),
            Objects.toString(c.sha256(), ""),
            Objects.toString(c.contentType(), ""),
            Objects.toString(c.businessType(), ""),
            Objects.toString(c.businessId(), ""));
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String normalizeNullableSha(String s) {
    return hasText(s) ? normalizeSha(s) : null;
  }

  private static String normalizeSha(String s) {
    String v = s.toLowerCase(Locale.ROOT);
    if (!v.matches("[0-9a-f]{64}"))
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_REQUEST, "SHA-256 must contain 64 hexadecimal characters");
    return v;
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }
}
