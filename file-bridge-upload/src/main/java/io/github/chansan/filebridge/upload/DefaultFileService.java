package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.service.*;
import io.github.chansan.filebridge.core.spi.*;
import java.io.*;
import java.net.URI;
import java.security.*;
import java.time.*;
import java.util.*;

/** 普通文件业务服务默认实现。 */
public final class DefaultFileService implements FileService {
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
  private final ContentTypeDetector contentTypes;
  private final long maximumFileSize;
  private final FileBridgeMetrics metrics;

  /**
   * 创建默认文件业务服务。
   *
   * @param files 文件仓储
   * @param idempotency 幂等记录仓储
   * @param tx 事务执行器
   * @param storages 存储注册表
   * @param defaultStorage 默认存储实例 ID
   * @param actors 当前可信身份提供器
   * @param policy 文件访问策略
   * @param quota 上传配额策略
   * @param keys 对象路径生成器
   * @param contentTypes 内容类型检测器
   * @param maximumFileSize 最大允许字节数
   * @param metrics 可观测性端口
   */
  public DefaultFileService(
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
      ContentTypeDetector contentTypes,
      long maximumFileSize,
      FileBridgeMetrics metrics) {
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
    this.contentTypes = contentTypes;
    this.maximumFileSize = maximumFileSize;
    this.metrics = metrics;
  }

  /**
   * {@inheritDoc}
   *
   * @param command 上传业务命令
   * @param source 文件内容输入流
   * @return 已创建文件元数据
   */
  @Override
  public FileMetadata upload(UploadFileCommand command, InputStream source) {
    long started = System.nanoTime();
    try {
      FileMetadata result = uploadInternal(command, source);
      metrics.record("upload", "success", result.size(), System.nanoTime() - started);
      return result;
    } catch (RuntimeException error) {
      metrics.record("upload", "failure", 0, System.nanoTime() - started);
      throw error;
    }
  }

  private FileMetadata uploadInternal(UploadFileCommand command, InputStream source) {
    Objects.requireNonNull(command);
    Objects.requireNonNull(source);
    // 身份必须来自宿主可信上下文，不能使用请求中伪造的用户字段。
    Actor actor = actors.currentActor();
    policy.checkUpload(actor, command.businessType(), command.businessId());
    long expected = command.expectedSize() == null ? -1 : command.expectedSize();
    if (expected >= 0) quota.check(actor, expected);
    // 幂等查询先于写存储，避免请求重试产生多份物理对象。
    String fingerprint = fingerprint(command);
    if (hasText(command.idempotencyKey())) {
      Optional<String> prior =
          idempotency.findResponse(
              actor.tenantId(),
              actor.ownerId(),
              "FILE_UPLOAD",
              command.idempotencyKey(),
              fingerprint);
      if (prior.isPresent()) return get(UUID.fromString(prior.get()));
    }
    StorageProvider storage = storages.require(defaultStorage);
    String key = keys.generate(actor, command.originalName());
    try (PushbackInputStream input =
        new PushbackInputStream(
            new BufferedInputStream(new SizeLimitedInputStream(source, maximumFileSize)), 8192)) {
      // 只读取有限文件头并回推到流中，避免为了类型识别把整个文件读入内存。
      byte[] prefix = input.readNBytes(8192);
      input.unread(prefix);
      String type =
          contentTypes.detect(prefix, command.originalName(), command.declaredContentType());
      StoredObject stored =
          storage.write(new ObjectWriteRequest(key, command.expectedSize(), type), input);
      quota.check(actor, stored.size());
      UUID objectId = UUID.randomUUID(), fileId = UUID.randomUUID();
      Instant now = Instant.now();
      StorageObjectRecord object =
          new StorageObjectRecord(
              objectId,
              stored.location(),
              stored.size(),
              stored.sha256(),
              stored.contentType(),
              StorageObjectStatus.AVAILABLE,
              now,
              now,
              now,
              0);
      FileReference reference =
          new FileReference(
              fileId,
              objectId,
              actor.tenantId(),
              actor.ownerId(),
              safeName(command.originalName()),
              command.businessType(),
              command.businessId(),
              FileReferenceStatus.ACTIVE,
              now,
              null);
      try {
        // 物理对象写入成功后，再原子创建对象元数据、业务引用和幂等结果。
        return tx.required(
            () -> {
              files.insertObject(object);
              files.insertReference(reference);
              if (hasText(command.idempotencyKey())) {
                String canonical =
                    idempotency.save(
                        actor.tenantId(),
                        actor.ownerId(),
                        "FILE_UPLOAD",
                        command.idempotencyKey(),
                        fingerprint,
                        fileId.toString(),
                        now.plus(Duration.ofHours(24)));
                if (!canonical.equals(fileId.toString())) {
                  throw new IdempotencyReplayException(canonical);
                }
              }
              return metadata(reference, object);
            });
      } catch (RuntimeException e) {
        try {
          storage.delete(stored.location());
        } catch (RuntimeException cleanup) {
          e.addSuppressed(cleanup);
          recordUntrackedObject(stored, cleanup);
        }
        if (e instanceof IdempotencyReplayException replay) {
          return get(UUID.fromString(replay.responseValue()));
        }
        throw e;
      }
    } catch (IOException e) {
      throw new FileBridgeException(
          FileBridgeErrorCode.STORAGE_FAILURE, "Failed to read upload", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param fileId 业务文件 ID
   * @return 文件元数据
   */
  @Override
  public FileMetadata get(UUID fileId) {
    Resolved r = resolve(fileId);
    return metadata(r.reference, r.object);
  }

  /**
   * {@inheritDoc}
   *
   * @param fileId 业务文件 ID
   * @return 可关闭下载资源，调用方必须关闭
   */
  @Override
  public FileResource download(UUID fileId) {
    Resolved r = resolve(fileId);
    return new FileResource(
        metadata(r.reference, r.object),
        new MetricsInputStream(
            storages.require(r.object.location().storageId()).open(r.object.location()), metrics));
  }

  /**
   * {@inheritDoc}
   *
   * @param fileId 业务文件 ID
   * @param validity 地址有效时长
   * @return 临时下载 URI
   */
  @Override
  public URI createAccessUrl(UUID fileId, Duration validity) {
    Resolved r = resolve(fileId);
    StorageProvider p = storages.require(r.object.location().storageId());
    if (!(p instanceof SignedUrlProvider signed))
      throw new FileBridgeException(
          FileBridgeErrorCode.CAPABILITY_NOT_SUPPORTED, "Storage does not support signed URLs");
    return signed.createDownloadUrl(r.object.location(), validity);
  }

  /**
   * {@inheritDoc}
   *
   * @param fileId 业务文件 ID
   */
  @Override
  public void delete(UUID fileId) {
    Resolved r = resolve(fileId);
    policy.checkDelete(actors.currentActor(), r.reference);
    tx.required(() -> files.markReferenceDeleted(fileId, Instant.now()));
  }

  /**
   * 校验权限并解析可用文件。
   *
   * @param id 业务文件 ID
   * @return 业务引用和物理对象
   */
  private void recordUntrackedObject(StoredObject stored, RuntimeException error) {
    Instant now = Instant.now();
    try {
      issues.upsert(
          "UNTRACKED_PHYSICAL_OBJECT:"
              + stored.location().storageId()
              + ":"
              + stored.location().objectKey(),
          "UNTRACKED_PHYSICAL_OBJECT",
          stored.location().storageId(),
          stored.location().bucket(),
          stored.location().objectKey(),
          null,
          null,
          error.getMessage(),
          now.plus(Duration.ofHours(1)),
          now);
    } catch (RuntimeException issueError) {
      error.addSuppressed(issueError);
    }
  }

  private Resolved resolve(UUID id) {
    FileReference ref =
        files
            .findReference(id)
            .filter(r -> r.status() == FileReferenceStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new FileBridgeException(FileBridgeErrorCode.FILE_NOT_FOUND, "File not found"));
    // 先校验业务引用权限，再解析物理对象，避免泄露底层存储信息。
    policy.checkRead(actors.currentActor(), ref);
    StorageObjectRecord obj =
        files
            .findObject(ref.objectId())
            .filter(o -> o.status() == StorageObjectStatus.AVAILABLE)
            .orElseThrow(
                () ->
                    new FileBridgeException(FileBridgeErrorCode.FILE_NOT_FOUND, "File not found"));
    return new Resolved(ref, obj);
  }

  /**
   * 合并业务引用和物理对象元数据。
   *
   * @param r 业务文件引用
   * @param o 物理对象记录
   * @return 对外文件元数据
   */
  private static FileMetadata metadata(FileReference r, StorageObjectRecord o) {
    return new FileMetadata(
        r.id(), r.originalName(), o.size(), o.sha256(), o.contentType(), r.status(), r.createdAt());
  }

  /**
   * 清理客户端文件名的路径和控制字符。
   *
   * @param n 原始文件名
   * @return 安全展示文件名
   */
  private static String safeName(String n) {
    if (n == null || n.isBlank()) return "file";
    String v = n.replace('\\', '/');
    v = v.substring(v.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\u0000]", "_");
    return v.isBlank() ? "file" : v;
  }

  /**
   * 计算上传命令幂等指纹。
   *
   * @param c 上传业务命令
   * @return SHA-256 指纹
   */
  private static String fingerprint(UploadFileCommand c) {
    return sha256(
        String.join(
            "\n",
            Objects.toString(c.originalName(), ""),
            Objects.toString(c.expectedSize(), ""),
            Objects.toString(c.declaredContentType(), ""),
            Objects.toString(c.businessType(), ""),
            Objects.toString(c.businessId(), "")));
  }

  /**
   * 计算 UTF-8 文本 SHA-256。
   *
   * @param s 待计算文本
   * @return 十六进制摘要
   */
  private static String sha256(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
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
   * 权限校验后的文件解析结果。
   *
   * @param reference 业务文件引用
   * @param object 物理对象记录
   */
  private record Resolved(FileReference reference, StorageObjectRecord object) {}
}
