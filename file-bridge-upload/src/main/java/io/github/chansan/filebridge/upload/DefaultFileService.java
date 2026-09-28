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
  private final TransactionRunner tx;
  private final StorageRegistry storages;
  private final String defaultStorage;
  private final CurrentActorProvider actors;
  private final FileAccessPolicy policy;
  private final UploadQuotaPolicy quota;
  private final ObjectKeyGenerator keys;
  private final ContentTypeDetector contentTypes;

  public DefaultFileService(
      FileRepository files,
      IdempotencyRepository idempotency,
      TransactionRunner tx,
      StorageRegistry storages,
      String defaultStorage,
      CurrentActorProvider actors,
      FileAccessPolicy policy,
      UploadQuotaPolicy quota,
      ObjectKeyGenerator keys,
      ContentTypeDetector contentTypes) {
    this.files = files;
    this.idempotency = idempotency;
    this.tx = tx;
    this.storages = storages;
    this.defaultStorage = defaultStorage;
    this.actors = actors;
    this.policy = policy;
    this.quota = quota;
    this.keys = keys;
    this.contentTypes = contentTypes;
  }

  @Override
  public FileMetadata upload(UploadFileCommand command, InputStream source) {
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
        new PushbackInputStream(new BufferedInputStream(source), 8192)) {
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
              if (hasText(command.idempotencyKey()))
                idempotency.save(
                    actor.tenantId(),
                    actor.ownerId(),
                    "FILE_UPLOAD",
                    command.idempotencyKey(),
                    fingerprint,
                    fileId.toString(),
                    now.plus(Duration.ofHours(24)));
              return metadata(reference, object);
            });
      } catch (RuntimeException e) {
        // 数据库事务失败时补偿删除已写入对象；补偿异常作为 suppressed 保留。
        try {
          storage.delete(stored.location());
        } catch (RuntimeException cleanup) {
          e.addSuppressed(cleanup);
        }
        throw e;
      }
    } catch (IOException e) {
      throw new FileBridgeException(
          FileBridgeErrorCode.STORAGE_FAILURE, "Failed to read upload", e);
    }
  }

  @Override
  public FileMetadata get(UUID fileId) {
    Resolved r = resolve(fileId);
    return metadata(r.reference, r.object);
  }

  @Override
  public FileResource download(UUID fileId) {
    Resolved r = resolve(fileId);
    return new FileResource(
        metadata(r.reference, r.object),
        storages.require(r.object.location().storageId()).open(r.object.location()));
  }

  @Override
  public URI createAccessUrl(UUID fileId, Duration validity) {
    Resolved r = resolve(fileId);
    StorageProvider p = storages.require(r.object.location().storageId());
    if (!(p instanceof SignedUrlProvider signed))
      throw new FileBridgeException(
          FileBridgeErrorCode.CAPABILITY_NOT_SUPPORTED, "Storage does not support signed URLs");
    return signed.createDownloadUrl(r.object.location(), validity);
  }

  @Override
  public void delete(UUID fileId) {
    Resolved r = resolve(fileId);
    policy.checkDelete(actors.currentActor(), r.reference);
    files.markReferenceDeleted(fileId, Instant.now());
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

  private static FileMetadata metadata(FileReference r, StorageObjectRecord o) {
    return new FileMetadata(
        r.id(), r.originalName(), o.size(), o.sha256(), o.contentType(), r.status(), r.createdAt());
  }

  private static String safeName(String n) {
    if (n == null || n.isBlank()) return "file";
    String v = n.replace('\\', '/');
    v = v.substring(v.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\u0000]", "_");
    return v.isBlank() ? "file" : v;
  }

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

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }

  private record Resolved(FileReference reference, StorageObjectRecord object) {}
}
