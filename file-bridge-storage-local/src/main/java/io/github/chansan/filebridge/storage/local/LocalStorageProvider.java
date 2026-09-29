package io.github.chansan.filebridge.storage.local;

import static java.nio.file.StandardCopyOption.*;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.spi.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** 支持流式读写和分片合并的本地文件系统适配器。 */
public final class LocalStorageProvider implements MultipartStorageProvider {
  private static final int BUFFER_SIZE = 64 * 1024;
  private static final System.Logger LOGGER =
      System.getLogger(LocalStorageProvider.class.getName());
  private final String storageId;
  private final Path root;
  private final Path temporaryRoot;

  /**
   * 创建本地存储适配器。
   *
   * @param storageId 存储实例 ID
   * @param root 可见对象根路径
   * @param temporaryRoot 临时文件根路径
   */
  public LocalStorageProvider(String storageId, Path root, Path temporaryRoot) {
    this.storageId = requireText(storageId, "storageId");
    this.root = normalize(root, "root");
    this.temporaryRoot = normalize(temporaryRoot, "temporaryRoot");
    if (this.root.equals(this.temporaryRoot))
      throw new IllegalArgumentException("root and temporaryRoot must differ");
    if (Files.isSymbolicLink(this.root) || Files.isSymbolicLink(this.temporaryRoot)) {
      throw new IllegalArgumentException("storage roots must not be symbolic links");
    }
    try {
      Files.createDirectories(this.root);
      Files.createDirectories(this.temporaryRoot);
      rejectSymbolicLinks(this.root, this.root);
      rejectSymbolicLinks(this.temporaryRoot, this.temporaryRoot);
    } catch (IOException e) {
      throw storageFailure("Cannot initialize local storage", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @return 存储实例 ID
   */
  @Override
  public String storageId() {
    return storageId;
  }

  /**
   * {@inheritDoc}
   *
   * @return 本地存储能力和分片限制
   */
  @Override
  public StorageCapabilities capabilities() {
    return StorageCapabilities.multipart(1, 5L * 1024 * 1024 * 1024, 10_000, false);
  }

  @Override
  public ObjectLocation locate(String objectKey) {
    return new ObjectLocation(storageId, null, objectKey);
  }

  /**
   * {@inheritDoc}
   *
   * @param request 服务端生成的对象路径、预期大小和内容类型
   * @param input 对象内容输入流
   * @return 已落盘对象元数据
   */
  @Override
  public StoredObject write(ObjectWriteRequest request, InputStream input) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(input, "input");
    // 最终对象永远先写临时文件，完整写入并校验后才移动到可见路径。
    Path target = resolve(root, request.objectKey());
    Path temp = temporaryRoot.resolve("objects").resolve(UUID.randomUUID() + ".tmp").normalize();
    try {
      Files.createDirectories(temp.getParent());
      Files.createDirectories(target.getParent());
      DigestResult result = copyWithDigest(input, temp, request.expectedSize());
      // 原子移动保证读取方不会看到半写入文件。
      moveAtomically(temp, target);
      return new StoredObject(
          new ObjectLocation(storageId, null, request.objectKey()),
          result.size(),
          result.sha256(),
          request.contentType(),
          Instant.now());
    } catch (IOException e) {
      deleteQuietly(temp);
      throw storageFailure("Failed to write local object", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param location 可信的对象定位信息
   * @return 对象输入流，调用方必须关闭
   */
  @Override
  public InputStream open(ObjectLocation location) {
    requireLocation(location);
    try {
      return new BufferedInputStream(
          Files.newInputStream(resolve(root, location.objectKey())), BUFFER_SIZE);
    } catch (NoSuchFileException e) {
      throw new FileBridgeException(FileBridgeErrorCode.FILE_NOT_FOUND, "Object does not exist");
    } catch (IOException e) {
      throw storageFailure("Failed to open local object", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param location 可信的对象定位信息
   * @return 对象存在时返回元数据，否则返回空
   */
  @Override
  public Optional<StoredObject> stat(ObjectLocation location) {
    requireLocation(location);
    Path path = resolve(root, location.objectKey());
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
      return Optional.of(
          new StoredObject(
              location,
              Files.size(path),
              null,
              Files.probeContentType(path),
              Files.getLastModifiedTime(path).toInstant()));
    } catch (IOException e) {
      throw storageFailure("Failed to inspect local object", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param location 可信的对象定位信息
   */
  @Override
  public void delete(ObjectLocation location) {
    requireLocation(location);
    try {
      Files.deleteIfExists(resolve(root, location.objectKey()));
    } catch (IOException e) {
      throw storageFailure("Failed to delete local object", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param objectKey 服务端生成的对象路径
   * @param contentType 对象内容类型
   * @return 平台上传句柄
   */
  @Override
  public MultipartUploadHandle initiateMultipart(String objectKey, String contentType) {
    resolve(root, objectKey);
    String uploadId = UUID.randomUUID().toString();
    try {
      Files.createDirectories(multipartDirectory(uploadId).resolve("parts"));
    } catch (IOException e) {
      throw storageFailure("Failed to initialize local multipart upload", e);
    }
    return new MultipartUploadHandle(uploadId, objectKey);
  }

  /**
   * {@inheritDoc}
   *
   * @param handle 平台上传句柄
   * @param partNumber 分片序号
   * @param contentLength 分片预期字节数
   * @param input 分片内容输入流
   * @return 本地确认的分片信息
   */
  @Override
  public UploadedPart uploadPart(
      MultipartUploadHandle handle, int partNumber, long contentLength, InputStream input) {
    requireHandle(handle);
    if (partNumber < 1) throw invalidPart("partNumber must start at 1");
    if (contentLength <= 0) throw invalidPart("part length must be positive");
    Path parts = multipartDirectory(handle.providerUploadId()).resolve("parts");
    Path target = parts.resolve(partNumber + ".part");
    Path metadata = parts.resolve(partNumber + ".properties");
    Path temp = parts.resolve(partNumber + "." + UUID.randomUUID() + ".tmp");
    try {
      Files.createDirectories(parts);
      DigestResult result = copyWithDigest(input, temp, contentLength);
      // 相同分片重试幂等成功，不同内容禁止静默覆盖。
      if (Files.exists(target)) {
        Properties existing = loadOrRepairPartMetadata(target, metadata);
        if (Long.toString(result.size()).equals(existing.getProperty("size"))
            && result.sha256().equals(existing.getProperty("sha256"))) {
          deleteQuietly(temp);
          return new UploadedPart(partNumber, result.size(), result.sha256(), result.sha256());
        }
        deleteQuietly(temp);
        throw new FileBridgeException(
            FileBridgeErrorCode.UPLOAD_PART_CONFLICT, "Part already exists with different content");
      }
      moveAtomically(temp, target);
      Properties values = new Properties();
      values.setProperty("size", Long.toString(result.size()));
      values.setProperty("sha256", result.sha256());
      try (OutputStream output = Files.newOutputStream(metadata, StandardOpenOption.CREATE_NEW)) {
        values.store(output, null);
      }
      return new UploadedPart(partNumber, result.size(), result.sha256(), result.sha256());
    } catch (FileBridgeException e) {
      throw e;
    } catch (IOException e) {
      deleteQuietly(temp);
      throw storageFailure("Failed to write local part", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param handle 平台上传句柄
   * @return 已存储分片，按序号升序排列
   */
  @Override
  public List<UploadedPart> listParts(MultipartUploadHandle handle) {
    requireHandle(handle);
    Path parts = multipartDirectory(handle.providerUploadId()).resolve("parts");
    if (!Files.isDirectory(parts)) return List.of();
    try (Stream<Path> stream = Files.list(parts)) {
      return stream
          .filter(p -> p.getFileName().toString().endsWith(".properties"))
          .map(p -> readPartMetadata(parts, p))
          .sorted(Comparator.comparingInt(UploadedPart::partNumber))
          .toList();
    } catch (IOException e) {
      throw storageFailure("Failed to list local parts", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param handle 平台上传句柄
   * @param expectedParts 请求完成时确认的分片集合
   * @param contentType 对象内容类型
   * @return 合并后的对象元数据
   */
  @Override
  public StoredObject completeMultipart(
      MultipartUploadHandle handle, List<UploadedPart> expectedParts, String contentType) {
    requireHandle(handle);
    if (expectedParts == null || expectedParts.isEmpty()) throw invalidPart("No parts supplied");
    List<UploadedPart> actual = listParts(handle);
    if (!actual.equals(
        expectedParts.stream().sorted(Comparator.comparingInt(UploadedPart::partNumber)).toList()))
      throw invalidPart("Stored parts do not match completion request");
    Path target = resolve(root, handle.objectKey());
    Path temp = multipartDirectory(handle.providerUploadId()).resolve("merged.tmp");
    MessageDigest digest = sha256();
    long total = 0;
    try {
      Files.createDirectories(target.getParent());
      try (OutputStream raw =
              new BufferedOutputStream(
                  Files.newOutputStream(
                      temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                  BUFFER_SIZE);
          DigestOutputStream output = new DigestOutputStream(raw, digest)) {
        byte[] buffer = new byte[BUFFER_SIZE];
        for (UploadedPart part : actual) {
          try (InputStream input =
              new BufferedInputStream(
                  Files.newInputStream(
                      multipartDirectory(handle.providerUploadId())
                          .resolve("parts")
                          .resolve(part.partNumber() + ".part")),
                  BUFFER_SIZE)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
              if (read > 0) {
                output.write(buffer, 0, read);
                total += read;
              }
            }
          }
        }
      }
      moveAtomically(temp, target);
      StoredObject result =
          new StoredObject(
              new ObjectLocation(storageId, null, handle.objectKey()),
              total,
              HexFormat.of().formatHex(digest.digest()),
              contentType,
              Instant.now());
      try {
        deleteTree(multipartDirectory(handle.providerUploadId()));
      } catch (FileBridgeException cleanupError) {
        LOGGER.log(
            System.Logger.Level.WARNING, "Unable to clean completed multipart data", cleanupError);
      }
      return result;
    } catch (IOException e) {
      deleteQuietly(temp);
      throw storageFailure("Failed to complete local multipart upload", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param handle 平台上传句柄
   */
  @Override
  public void abortMultipart(MultipartUploadHandle handle) {
    requireHandle(handle);
    deleteTree(multipartDirectory(handle.providerUploadId()));
  }

  /**
   * 读取本地分片元数据。
   *
   * @param parts 分片目录
   * @param metadata 元数据文件路径
   * @return 已存储分片信息
   */
  private UploadedPart readPartMetadata(Path parts, Path metadata) {
    try {
      String name = metadata.getFileName().toString();
      int number = Integer.parseInt(name.substring(0, name.indexOf('.')));
      Properties p = loadProperties(metadata);
      long size = Long.parseLong(p.getProperty("size"));
      String sha = p.getProperty("sha256");
      if (!Files.isRegularFile(parts.resolve(number + ".part")))
        throw new IOException("part body is missing");
      return new UploadedPart(number, size, sha, sha);
    } catch (IOException | RuntimeException e) {
      throw storageFailure("Invalid local part metadata", e);
    }
  }

  /**
   * 加载属性文件。
   *
   * @param path 属性文件路径
   * @return 已加载属性
   * @throws IOException 文件读取失败
   */
  private static Properties loadOrRepairPartMetadata(Path body, Path metadata) throws IOException {
    if (Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
      return loadProperties(metadata);
    }
    DigestResult digest;
    try (InputStream input = Files.newInputStream(body)) {
      digest = digest(input);
    }
    Properties repaired = new Properties();
    repaired.setProperty("size", Long.toString(digest.size()));
    repaired.setProperty("sha256", digest.sha256());
    Path temporary =
        metadata.resolveSibling(metadata.getFileName() + ".repair-" + UUID.randomUUID());
    try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
      repaired.store(output, null);
    }
    moveAtomically(temporary, metadata);
    return repaired;
  }

  private static DigestResult digest(InputStream input) throws IOException {
    MessageDigest digest = sha256();
    long total = 0;
    byte[] buffer = new byte[BUFFER_SIZE];
    int read;
    while ((read = input.read(buffer)) >= 0) {
      if (read > 0) {
        digest.update(buffer, 0, read);
        total += read;
      }
    }
    return new DigestResult(total, HexFormat.of().formatHex(digest.digest()));
  }

  private static void rejectSymbolicLinks(Path base, Path target) {
    Path current = base;
    if (Files.isSymbolicLink(current)) {
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_REQUEST, "Storage path contains a symbolic link");
    }
    for (Path segment : base.relativize(target)) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
        throw new FileBridgeException(
            FileBridgeErrorCode.INVALID_REQUEST, "Storage path contains a symbolic link");
      }
    }
  }

  private static Properties loadProperties(Path path) throws IOException {
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(path)) {
      p.load(in);
    }
    return p;
  }

  /**
   * 定位分片上传临时目录。
   *
   * @param id 平台上传 ID
   * @return 安全归一化后的目录路径
   */
  private Path multipartDirectory(String id) {
    return resolve(temporaryRoot.resolve("multipart"), id);
  }

  /**
   * 归一化并校验目录路径。
   *
   * @param path 原始目录路径
   * @param name 参数名称
   * @return 绝对归一化目录路径
   */
  private static Path normalize(Path path, String name) {
    if (path == null) throw new IllegalArgumentException(name + " must not be null");
    return path.toAbsolutePath().normalize();
  }

  /**
   * 安全解析对象路径并阻断路径穿越。
   *
   * @param base 根路径
   * @param key 对象路径
   * @return 仍位于根路径内的目标路径
   */
  private static Path resolve(Path base, String key) {
    if (key == null || key.isBlank() || key.indexOf('\0') >= 0)
      throw new IllegalArgumentException("invalid object key");
    Path p = base.resolve(key).normalize();
    // 归一化后再次校验根路径，阻断 ../ 路径穿越。
    if (!p.startsWith(base))
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_REQUEST, "Object path escapes storage root");
    rejectSymbolicLinks(base, p);
    return p;
  }

  /**
   * 校验对象定位信息归属。
   *
   * @param location 待校验定位信息
   */
  private void requireLocation(ObjectLocation location) {
    if (location == null || !storageId.equals(location.storageId()))
      throw new IllegalArgumentException("location belongs to another storage");
  }

  /**
   * 校验分片上传句柄有效性。
   *
   * @param h 待校验上传句柄
   */
  private void requireHandle(MultipartUploadHandle h) {
    if (h == null
        || h.providerUploadId() == null
        || h.providerUploadId().contains("/")
        || h.providerUploadId().contains("\\"))
      throw new IllegalArgumentException("invalid multipart handle");
  }

  /**
   * 校验文本字段并返回原始值。
   *
   * @param v 待校验文本
   * @param n 参数名称
   * @return 非空白的文本值
   */
  private static String requireText(String v, String n) {
    if (v == null || v.isBlank()) throw new IllegalArgumentException(n + " must not be blank");
    return v;
  }

  /**
   * 流式复制内容并计算大小与摘要。
   *
   * @param input 内容输入流
   * @param path 临时目标文件
   * @param expected 预期字节数，可为空
   * @return 实际大小和 SHA-256
   * @throws IOException 文件读写失败
   */
  private static DigestResult copyWithDigest(InputStream input, Path path, Long expected)
      throws IOException {
    MessageDigest digest = sha256();
    long total = 0;
    byte[] buffer = new byte[BUFFER_SIZE];
    try (OutputStream raw =
            new BufferedOutputStream(
                Files.newOutputStream(path, StandardOpenOption.CREATE_NEW), BUFFER_SIZE);
        DigestOutputStream out = new DigestOutputStream(raw, digest)) {
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (read > 0) {
          out.write(buffer, 0, read);
          total += read;
          if (expected != null && total > expected)
            throw new FileBridgeException(
                FileBridgeErrorCode.INVALID_REQUEST, "Received more bytes than declared");
        }
      }
    }
    if (expected != null && total != expected)
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_REQUEST, "Received size does not match declared size");
    return new DigestResult(total, HexFormat.of().formatHex(digest.digest()));
  }

  /**
   * 创建 SHA-256 摘要器。
   *
   * @return SHA-256 摘要器
   */
  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 优先执行原子移动。
   *
   * @param from 来源文件
   * @param to 目标文件
   * @throws IOException 文件移动失败
   */
  private static void moveAtomically(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to);
    }
  }

  /**
   * 静默删除单个文件或空目录。
   *
   * @param p 待删除路径
   */
  private static void deleteQuietly(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignored) {
    }
  }

  /**
   * 递归删除目录树。
   *
   * @param root 待删除根目录
   */
  private static void deleteTree(Path root) {
    if (!Files.exists(root)) return;
    try (Stream<Path> s = Files.walk(root)) {
      s.sorted(Comparator.reverseOrder()).forEach(LocalStorageProvider::deleteQuietly);
    } catch (IOException e) {
      throw storageFailure("Failed to clean local multipart data", e);
    }
  }

  /**
   * 创建存储失败异常。
   *
   * @param m 可读错误信息
   * @param e 原始异常
   * @return 领域异常
   */
  private static FileBridgeException storageFailure(String m, Throwable e) {
    return new FileBridgeException(FileBridgeErrorCode.STORAGE_FAILURE, m, e);
  }

  /**
   * 创建无效分片异常。
   *
   * @param m 可读错误信息
   * @return 领域异常
   */
  private static FileBridgeException invalidPart(String m) {
    return new FileBridgeException(FileBridgeErrorCode.INVALID_PART, m);
  }

  /**
   * 流式复制结果。
   *
   * @param size 实际写入字节数
   * @param sha256 实际内容 SHA-256
   */
  private record DigestResult(long size, String sha256) {}
}
