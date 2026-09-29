package io.github.chansan.filebridge.storage.minio;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.spi.*;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import java.io.*;
import java.net.URI;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletionException;

/** MinIO 对象存储适配器。 */
public final class MinioStorageProvider
    implements MultipartStorageProvider, SignedUrlProvider, AutoCloseable {
  private final String id, bucket;
  private final MinioAsyncClient client;

  /**
   * 创建 MinIO 存储适配器。
   *
   * @param id 存储实例 ID
   * @param bucket Bucket 名称
   * @param client MinIO 客户端
   */
  public MinioStorageProvider(String id, String bucket, MinioAsyncClient client) {
    this.id = id;
    this.bucket = bucket;
    this.client = client;
  }

  /**
   * {@inheritDoc}
   *
   * @return 存储实例 ID
   */
  @Override
  public String storageId() {
    return id;
  }

  /**
   * {@inheritDoc}
   *
   * @return MinIO 能力和分片限制
   */
  @Override
  public StorageCapabilities capabilities() {
    return StorageCapabilities.multipart(5L << 20, 5L << 30, 10000, true);
  }

  /**
   * {@inheritDoc}
   *
   * @param r 服务端生成的对象路径、预期大小和内容类型
   * @param input 对象内容输入流
   * @return 已上传对象元数据
   */
  @Override
  public StoredObject write(ObjectWriteRequest r, InputStream input) {
    MessageDigest d = sha();
    CountingInputStream count = new CountingInputStream(new DigestInputStream(input, d));
    try {
      client
          .putObject(
              PutObjectArgs.builder()
                  .bucket(bucket)
                  .object(r.objectKey())
                  .contentType(r.contentType())
                  .stream(count, r.expectedSize() == null ? -1L : r.expectedSize(), 10L << 20)
                  .build())
          .join();
      if (r.expectedSize() != null && count.count != r.expectedSize())
        throw new FileBridgeException(
            FileBridgeErrorCode.INVALID_REQUEST, "Received size does not match declared size");
      return new StoredObject(
          new ObjectLocation(id, bucket, r.objectKey()),
          count.count,
          HexFormat.of().formatHex(d.digest()),
          r.contentType(),
          Instant.now());
    } catch (Exception e) {
      throw fail("MinIO putObject failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param l 可信的对象定位信息
   * @return 对象输入流，调用方必须关闭
   */
  @Override
  public InputStream open(ObjectLocation l) {
    check(l);
    try {
      return client
          .getObject(GetObjectArgs.builder().bucket(bucket).object(l.objectKey()).build())
          .join();
    } catch (Exception e) {
      throw fail("MinIO getObject failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param l 可信的对象定位信息
   * @return 对象存在时返回元数据，否则返回空
   */
  @Override
  public Optional<StoredObject> stat(ObjectLocation l) {
    check(l);
    try {
      StatObjectResponse s =
          client
              .statObject(StatObjectArgs.builder().bucket(bucket).object(l.objectKey()).build())
              .join();
      return Optional.of(
          new StoredObject(l, s.size(), null, s.contentType(), s.lastModified().toInstant()));
    } catch (Exception e) {
      if (isNotFound(e)) return Optional.empty();
      throw fail("MinIO statObject failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param l 可信的对象定位信息
   */
  @Override
  public void delete(ObjectLocation l) {
    check(l);
    try {
      client
          .removeObject(RemoveObjectArgs.builder().bucket(bucket).object(l.objectKey()).build())
          .join();
    } catch (Exception e) {
      throw fail("MinIO removeObject failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param l 可信的对象定位信息
   * @param validity 地址有效时长
   * @return 临时下载 URI
   */
  @Override
  public URI createDownloadUrl(ObjectLocation l, Duration validity) {
    check(l);
    try {
      return URI.create(
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Http.Method.GET)
                  .bucket(bucket)
                  .object(l.objectKey())
                  .expiry(Math.toIntExact(validity.toSeconds()))
                  .build()));
    } catch (Exception e) {
      throw fail("MinIO presign failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param key 服务端生成的对象路径
   * @param type 对象内容类型
   * @return 平台上传句柄
   */
  @Override
  public MultipartUploadHandle initiateMultipart(String key, String type) {
    try {
      var response =
          client
              .createMultipartUpload(
                  CreateMultipartUploadArgs.builder().bucket(bucket).object(key).build())
              .join();
      return new MultipartUploadHandle(response.result().uploadId(), key);
    } catch (Exception e) {
      throw fail("MinIO multipart initialization failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param h 平台上传句柄
   * @param number 分片序号
   * @param length 分片预期字节数
   * @param input 分片内容输入流
   * @return 服务端确认的分片信息
   */
  @Override
  public UploadedPart uploadPart(
      MultipartUploadHandle h, int number, long length, InputStream input) {
    MessageDigest d = sha();
    // MinIO SDK 的单分片缓冲区受服务端协商的分片大小上限约束，不随整文件增长。
    try (io.minio.ByteBuffer buffer = new io.minio.ByteBuffer(length);
        DigestOutputStream out = new DigestOutputStream(buffer, d)) {
      input.transferTo(out);
      out.flush();
      if (buffer.length() != length)
        throw new FileBridgeException(FileBridgeErrorCode.INVALID_PART, "Part length mismatch");
      var response =
          client
              .uploadPart(
                  UploadPartArgs.builder()
                      .bucket(bucket)
                      .object(h.objectKey())
                      .uploadId(h.providerUploadId())
                      .partNumber(number)
                      .buffer(buffer)
                      .build())
              .join();
      // ETag 仅用于平台完成请求，可信 SHA-256 由服务端独立计算。
      String hash = HexFormat.of().formatHex(d.digest());
      return new UploadedPart(number, length, hash, response.part().etag());
    } catch (Exception e) {
      throw fail("MinIO uploadPart failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param h 平台上传句柄
   * @return 服务端已存分片列表
   */
  @Override
  public List<UploadedPart> listParts(MultipartUploadHandle h) {
    try {
      var result =
          client
              .listParts(
                  ListPartsArgs.builder()
                      .bucket(bucket)
                      .uploadId(h.providerUploadId())
                      .maxParts(10000)
                      .build())
              .join()
              .result();
      return result.parts().stream()
          .map(p -> new UploadedPart(p.partNumber(), p.partSize(), null, p.etag()))
          .toList();
    } catch (Exception e) {
      throw fail("MinIO listParts failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param h 平台上传句柄
   * @param parts 请求完成时确认的分片集合
   * @param type 对象内容类型
   * @return 已合并对象元数据
   */
  @Override
  public StoredObject completeMultipart(
      MultipartUploadHandle h, List<UploadedPart> parts, String type) {
    try {
      io.minio.messages.Part[] values =
          parts.stream()
              .sorted(Comparator.comparingInt(UploadedPart::partNumber))
              .map(p -> new io.minio.messages.Part(p.partNumber(), p.providerPartTag()))
              .toArray(io.minio.messages.Part[]::new);
      client
          .completeMultipartUpload(
              CompleteMultipartUploadArgs.builder()
                  .bucket(bucket)
                  .object(h.objectKey())
                  .uploadId(h.providerUploadId())
                  .parts(values)
                  .build())
          .join();
      return stat(new ObjectLocation(id, bucket, h.objectKey())).orElseThrow();
    } catch (Exception e) {
      throw fail("MinIO completeMultipart failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param h 平台上传句柄
   */
  @Override
  public void abortMultipart(MultipartUploadHandle h) {
    try {
      client
          .abortMultipartUpload(
              AbortMultipartUploadArgs.builder()
                  .bucket(bucket)
                  .object(h.objectKey())
                  .uploadId(h.providerUploadId())
                  .build())
          .join();
    } catch (Exception e) {
      throw fail("MinIO abortMultipart failed", e);
    }
  }

  /**
   * 校验对象定位信息归属。
   *
   * @param l 待校验定位信息
   */
  @Override
  public void close() throws Exception {
    client.close();
  }

  private void check(ObjectLocation l) {
    if (l == null || !id.equals(l.storageId()) || !bucket.equals(l.bucket()))
      throw new IllegalArgumentException("Object belongs to another storage");
  }

  /**
   * 创建 SHA-256 摘要器。
   *
   * @return SHA-256 摘要器
   */
  private static MessageDigest sha() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 解包异步异常并创建存储失败异常。
   *
   * @param m 可读错误信息
   * @param e 原始异常
   * @return 领域异常
   */
  private static boolean isNotFound(Throwable error) {
    Throwable current = unwrap(error);
    if (!(current instanceof ErrorResponseException response)) return false;
    String code = response.errorResponse().code();
    return "NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NotFound".equals(code);
  }

  private static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static FileBridgeException fail(String message, Throwable error) {
    Throwable cause = unwrap(error);
    if (cause instanceof FileBridgeException fileBridgeException) return fileBridgeException;
    return new FileBridgeException(FileBridgeErrorCode.STORAGE_FAILURE, message, cause);
  }

  private static final class CountingInputStream extends FilterInputStream {
    long count;

    /**
     * 包装待计数的输入流。
     *
     * @param in 原始输入流
     */
    CountingInputStream(InputStream in) {
      super(in);
    }

    /**
     * 读取单字节并累计成功读取数。
     *
     * @return 字节值，流结束时返回 {@code -1}
     * @throws IOException 流读取失败
     */
    @Override
    public int read() throws IOException {
      int v = super.read();
      if (v >= 0) count++;
      return v;
    }

    /**
     * 读取字节数组并累计成功读取数。
     *
     * @param b 目标缓冲区
     * @param o 写入偏移量
     * @param l 最大读取长度
     * @return 实际读取长度，流结束时返回 {@code -1}
     * @throws IOException 流读取失败
     */
    @Override
    public int read(byte[] b, int o, int l) throws IOException {
      int n = super.read(b, o, l);
      if (n > 0) count += n;
      return n;
    }
  }
}
