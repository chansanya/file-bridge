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
  private final String storageId;
  private final Path root;
  private final Path temporaryRoot;

  public LocalStorageProvider(String storageId, Path root, Path temporaryRoot) {
    this.storageId = requireText(storageId, "storageId");
    this.root = normalize(root, "root");
    this.temporaryRoot = normalize(temporaryRoot, "temporaryRoot");
    if (this.root.equals(this.temporaryRoot))
      throw new IllegalArgumentException("root and temporaryRoot must differ");
    try {
      Files.createDirectories(this.root);
      Files.createDirectories(this.temporaryRoot);
    } catch (IOException e) {
      throw storageFailure("Cannot initialize local storage", e);
    }
  }

  @Override
  public String storageId() {
    return storageId;
  }

  @Override
  public StorageCapabilities capabilities() {
    return StorageCapabilities.multipart(1, 5L * 1024 * 1024 * 1024, 10_000, false);
  }

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

  @Override
  public void delete(ObjectLocation location) {
    requireLocation(location);
    try {
      Files.deleteIfExists(resolve(root, location.objectKey()));
    } catch (IOException e) {
      throw storageFailure("Failed to delete local object", e);
    }
  }

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
        Properties existing = loadProperties(metadata);
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
      deleteTree(multipartDirectory(handle.providerUploadId()));
      return result;
    } catch (IOException e) {
      deleteQuietly(temp);
      throw storageFailure("Failed to complete local multipart upload", e);
    }
  }

  @Override
  public void abortMultipart(MultipartUploadHandle handle) {
    requireHandle(handle);
    deleteTree(multipartDirectory(handle.providerUploadId()));
  }

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

  private static Properties loadProperties(Path path) throws IOException {
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(path)) {
      p.load(in);
    }
    return p;
  }

  private Path multipartDirectory(String id) {
    return resolve(temporaryRoot.resolve("multipart"), id);
  }

  private static Path normalize(Path path, String name) {
    if (path == null) throw new IllegalArgumentException(name + " must not be null");
    return path.toAbsolutePath().normalize();
  }

  private static Path resolve(Path base, String key) {
    if (key == null || key.isBlank() || key.indexOf('\0') >= 0)
      throw new IllegalArgumentException("invalid object key");
    Path p = base.resolve(key).normalize();
    // 归一化后再次校验根路径，阻断 ../ 路径穿越。
    if (!p.startsWith(base))
      throw new FileBridgeException(
          FileBridgeErrorCode.INVALID_REQUEST, "Object path escapes storage root");
    return p;
  }

  private void requireLocation(ObjectLocation location) {
    if (location == null || !storageId.equals(location.storageId()))
      throw new IllegalArgumentException("location belongs to another storage");
  }

  private void requireHandle(MultipartUploadHandle h) {
    if (h == null
        || h.providerUploadId() == null
        || h.providerUploadId().contains("/")
        || h.providerUploadId().contains("\\"))
      throw new IllegalArgumentException("invalid multipart handle");
  }

  private static String requireText(String v, String n) {
    if (v == null || v.isBlank()) throw new IllegalArgumentException(n + " must not be blank");
    return v;
  }

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

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void moveAtomically(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to);
    }
  }

  private static void deleteQuietly(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignored) {
    }
  }

  private static void deleteTree(Path root) {
    if (!Files.exists(root)) return;
    try (Stream<Path> s = Files.walk(root)) {
      s.sorted(Comparator.reverseOrder()).forEach(LocalStorageProvider::deleteQuietly);
    } catch (IOException e) {
      throw storageFailure("Failed to clean local multipart data", e);
    }
  }

  private static FileBridgeException storageFailure(String m, Throwable e) {
    return new FileBridgeException(FileBridgeErrorCode.STORAGE_FAILURE, m, e);
  }

  private static FileBridgeException invalidPart(String m) {
    return new FileBridgeException(FileBridgeErrorCode.INVALID_PART, m);
  }

  private record DigestResult(long size, String sha256) {}
}
