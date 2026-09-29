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
  private static final System.Logger LOGGER =
      System.getLogger(DefaultUploadService.class.getName());
  private final UploadRepository uploads;
  private final FileRepository files;
  private final IdempotencyRepository idempotency;
  private final ReconciliationRepository issues;
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

  /**
   * 创建默认分片上传服务。
   *
   * @param uploads 上传任务仓储
   * @param files 文件仓储
   * @param idempotency 幂等记录仓储
   * @param tx 事务执行器
   * @param storages 存储注册表
   * @param defaultStorage 默认存储实例 ID
   * @param actors 当前可信身份提供器
   * @param policy 文件访问策略
   * @param quota 上传配额策略
   * @param keys 对象路径生成器
   * @param scope 秒传授权范围
   * @param preferredPartSize 推荐分片字节数
   * @param taskTtl 任务保留时长
   */
  public DefaultUploadService(
      UploadRepository uploads,
      FileRepository files,
      IdempotencyRepository idempotency,
      ReconciliationRepository issues,
      TransactionRunner tx,
      StorageRegistry storages,
      String defaultStorage,
      CurrentActorProvider actors,
      FileAccessPolicy policy,
      UploadQuotaPolicy quota,
      ObjectKeyGenerator keys,
      DeduplicationScope scope,
      long preferredPartSize,
      Duration taskTtl) {
    this.uploads = uploads;
    this.files = files;
    this.idempotency = idempotency;
    this.issues = issues;
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
  }

  /**
   * {@inheritDoc}
   *
   * @param c 上传初始化命令
   * @return 秒传或分片上传初始化结果
   */
  @Override
  public UploadInitialization initialize(InitializeUploadCommand c) {
    LOGGER.log(
        System.Logger.Level.INFO,
        "Multipart upload initialization started storageId={0} bytes={1}",
        defaultStorage,
        c.size());
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
      if (prior.isPresent()) {
        UploadInitialization replay = decode(prior.get());
        logInitialization("idempotency-replay", replay, c.size());
        return replay;
      }
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
        UUID fileId;
        try {
          fileId =
              tx.required(
                  () -> {
                    files.insertReference(copy);
                    if (hasText(c.idempotencyKey())) {
                      String response = "INSTANT:" + copy.id();
                      String canonical =
                          idempotency.save(
                              actor.tenantId(),
                              actor.ownerId(),
                              "UPLOAD_INIT",
                              c.idempotencyKey(),
                              requestHash,
                              response,
                              Instant.now().plus(Duration.ofHours(24)));
                      if (!canonical.equals(response)) {
                        throw new IdempotencyReplayException(canonical);
                      }
                    }
                    return copy.id();
                  });
        } catch (IdempotencyReplayException replay) {
          UploadInitialization result = decode(replay.responseValue());
          logInitialization("concurrent-replay", result, c.size());
          return result;
        }
        UploadInitialization result = UploadInitialization.instant(fileId);
        logInitialization("instant", result, c.size());
        return result;
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
            if (hasText(c.idempotencyKey())) {
              String response = "UPLOAD:" + id;
              String canonical =
                  idempotency.save(
                      actor.tenantId(),
                      actor.ownerId(),
                      "UPLOAD_INIT",
                      c.idempotencyKey(),
                      requestHash,
                      response,
                      now.plus(Duration.ofHours(24)));
              if (!canonical.equals(response)) {
                throw new IdempotencyReplayException(canonical);
              }
            }
          });
    } catch (RuntimeException e) {
      try {
        multipart.abortMultipart(handle);
      } catch (RuntimeException cleanup) {
        e.addSuppressed(cleanup);
      }
      if (e instanceof IdempotencyReplayException replay) {
        UploadInitialization result = decode(replay.responseValue());
        logInitialization("concurrent-replay", result, c.size());
        return result;
      }
      throw e;
    }
    UploadInitialization result =
        UploadInitialization.upload(id, partSize, total, task.expiresAt());
    LOGGER.log(
        System.Logger.Level.INFO,
        "Multipart upload initialized uploadId={0} storageId={1} bytes={2} partSize={3} totalParts={4} expiresAt={5}",
        id,
        defaultStorage,
        c.size(),
        partSize,
        total,
        task.expiresAt());
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @param number 分片序号
   * @param length 分片预期字节数
   * @param claimedSha 客户端声明的分片摘要，可为空
   * @param input 分片内容输入流
   * @return 已保存分片记录
   */
  @Override
  public UploadPart uploadPart(
      UUID id, int number, Long length, String claimedSha, InputStream input) {
    UploadTask task = requireAccessible(id);
    ensureActive(task);
    validatePart(task, number, length);
    MultipartStorageProvider p = asMultipart(task);
    // 只有存储平台确认成功后，才把分片记录为 COMPLETED。
    UploadedPart stored;
    try {
      stored =
          p.uploadPart(
              new MultipartUploadHandle(task.providerUploadId(), task.objectKey()),
              number,
              length,
              input);
    } catch (RuntimeException error) {
      logPartFailure(task, number, length, error);
      throw error;
    }
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
    LOGGER.log(
        System.Logger.Level.DEBUG,
        "Multipart part stored uploadId={0} storageId={1} part={2}/{3} bytes={4}",
        id,
        task.storageId(),
        number,
        task.totalParts(),
        stored.size());
    return saved;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @return 上传任务状态
   */
  @Override
  public UploadStatusView get(UUID id) {
    UploadTask t = requireAccessible(id);
    return view(t);
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   * @return 触发完成处理后的任务状态
   */
  @Override
  public UploadStatusView requestCompletion(UUID id) {
    UploadTask t = requireAccessible(id);
    if (t.status() == UploadTaskStatus.COMPLETED) {
      LOGGER.log(
          System.Logger.Level.INFO,
          "Multipart completion replay uploadId={0} fileId={1} storageId={2}",
          id,
          t.resultFileId(),
          t.storageId());
      return view(t);
    }
    ensureActive(t);
    List<UploadPart> parts = uploads.findCompletedParts(id);
    if (parts.size() != t.totalParts())
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_UPLOAD_STATE, "Not all parts are uploaded");
    Instant requestedAt = Instant.now();
    uploads.requestCompletion(id, requestedAt);
    UploadStatusView result = view(uploads.findTask(id).orElse(t));
    LOGGER.log(
        System.Logger.Level.INFO,
        "Multipart completion requested uploadId={0} storageId={1} totalParts={2}",
        id,
        t.storageId(),
        t.totalParts());
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * @param id 上传任务 ID
   */
  @Override
  public void cancel(UUID id) {
    UploadTask t = requireAccessible(id);
    if (t.status() == UploadTaskStatus.COMPLETED) {
      LOGGER.log(
          System.Logger.Level.DEBUG,
          "Multipart cancellation ignored uploadId={0} status={1}",
          id,
          t.status());
      return;
    }
    if (uploads.cancel(id, Instant.now())) {
      try {
        asMultipart(t)
            .abortMultipart(new MultipartUploadHandle(t.providerUploadId(), t.objectKey()));
        LOGGER.log(
            System.Logger.Level.INFO,
            "Multipart upload cancelled uploadId={0} storageId={1}",
            id,
            t.storageId());
      } catch (RuntimeException error) {
        recordAbortFailure(t, error);
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Multipart cancellation requires reconciliation uploadId="
                + id
                + " storageId="
                + t.storageId()
                + " errorCode="
                + errorCode(error),
            error);
      }
    }
  }

  /**
   * 校验访问权限并加载上传任务。
   *
   * @param id 上传任务 ID
   * @return 当前用户可访问的上传任务
   */
  private void recordAbortFailure(UploadTask task, RuntimeException error) {
    Instant now = Instant.now();
    ObjectLocation location = storages.require(task.storageId()).locate(task.objectKey());
    issues.upsert(
        "MULTIPART_ABORT_FAILED:" + task.id(),
        "MULTIPART_ABORT_FAILED",
        task.storageId(),
        location.bucket(),
        task.objectKey(),
        task.providerUploadId(),
        task.id().toString(),
        error.getMessage(),
        now.plus(Duration.ofMinutes(5)),
        now);
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

  /**
   * 校验任务未过期且处于可继续上传状态。
   *
   * @param t 上传任务
   */
  private void ensureActive(UploadTask t) {
    if (t.expiresAt().isBefore(Instant.now()))
      throw new FileBridgeException(FileBridgeErrorCode.UPLOAD_EXPIRED, "Upload expired");
    if (t.status() != UploadTaskStatus.CREATED && t.status() != UploadTaskStatus.UPLOADING)
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_UPLOAD_STATE,
          "Operation is not allowed in state " + t.status());
  }

  /**
   * 加载任务目标存储并确认分片能力。
   *
   * @param t 上传任务
   * @return 分片存储适配器
   */
  private MultipartStorageProvider asMultipart(UploadTask t) {
    StorageProvider p = storages.require(t.storageId());
    if (p instanceof MultipartStorageProvider m) return m;
    throw new FileBridgeException(
        FileBridgeErrorCode.CAPABILITY_NOT_SUPPORTED, "Storage does not support multipart uploads");
  }

  /**
   * 校验分片序号和声明长度。
   *
   * @param t 上传任务
   * @param n 分片序号
   * @param len 客户端声明的分片字节数
   */
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

  /**
   * 组装对外上传状态视图。
   *
   * @param t 上传任务
   * @return 上传任务状态视图
   */
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

  /**
   * 解析幂等响应为初始化结果。
   *
   * @param value 已保存的紧凑响应值
   * @return 秒传或分片上传初始化结果
   */
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

  /**
   * 计算上传初始化幂等指纹。
   *
   * @param c 上传初始化命令
   * @return SHA-256 指纹
   */
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

  /**
   * 归一化可空 SHA-256。
   *
   * @param s 客户端声明摘要
   * @return 小写十六进制摘要，空输入返回 {@code null}
   */
  private static String normalizeNullableSha(String s) {
    return hasText(s) ? normalizeSha(s) : null;
  }

  /**
   * 归一化并校验 SHA-256。
   *
   * @param s 声明摘要
   * @return 小写十六进制摘要
   */
  private static String normalizeSha(String s) {
    String v = s.toLowerCase(Locale.ROOT);
    if (!v.matches("[0-9a-f]{64}"))
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_REQUEST, "SHA-256 must contain 64 hexadecimal characters");
    return v;
  }

  /**
   * 判断文本是否包含非空白内容。
   *
   * @param s 待判断文本
   * @return 非空且包含非空白字符时返回 {@code true}
   */
  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }

  /**
   * 记录秒传或幂等重放后的初始化结果。
   *
   * @param outcome 初始化结果来源
   * @param result 初始化结果
   * @param bytes 文件字节数
   */
  private void logInitialization(String outcome, UploadInitialization result, long bytes) {
    LOGGER.log(
        System.Logger.Level.INFO,
        "Multipart initialization resolved outcome={0} mode={1} uploadId={2} fileId={3} storageId={4} bytes={5}",
        outcome,
        result.mode(),
        result.uploadId(),
        result.fileId(),
        defaultStorage,
        bytes);
  }

  /**
   * 记录单个分片失败上下文；已知业务异常不重复打印堆栈。
   *
   * @param task 上传任务
   * @param partNumber 分片序号
   * @param expectedBytes 预期字节数
   * @param error 失败异常
   */
  private static void logPartFailure(
      UploadTask task, int partNumber, Long expectedBytes, RuntimeException error) {
    String message =
        "Multipart part failed uploadId="
            + task.id()
            + " storageId="
            + task.storageId()
            + " part="
            + partNumber
            + "/"
            + task.totalParts()
            + " expectedBytes="
            + expectedBytes
            + " errorCode="
            + errorCode(error);
    if (error instanceof FileBridgeException) {
      LOGGER.log(System.Logger.Level.WARNING, message);
    } else {
      LOGGER.log(System.Logger.Level.ERROR, message, error);
    }
  }

  /**
   * 返回稳定业务错误码，未知异常退化为异常类型名。
   *
   * @param error 失败异常
   * @return 错误码或异常类型名
   */
  private static String errorCode(RuntimeException error) {
    return error instanceof FileBridgeException fileBridgeError
        ? fileBridgeError.code().name()
        : error.getClass().getSimpleName();
  }
}
