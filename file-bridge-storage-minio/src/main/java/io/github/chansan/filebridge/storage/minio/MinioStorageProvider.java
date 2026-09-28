package io.github.chansan.filebridge.storage.minio;

import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.spi.*;
import io.minio.*;
import java.io.*;
import java.net.URI;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletionException;

/** MinIO 对象存储适配器。 */
public final class MinioStorageProvider implements MultipartStorageProvider, SignedUrlProvider {
  private final String id, bucket;
  private final MinioAsyncClient client;

  public MinioStorageProvider(String id, String bucket, MinioAsyncClient client) {
    this.id = id;
    this.bucket = bucket;
    this.client = client;
  }

  @Override
  public String storageId() {
    return id;
  }

  @Override
  public StorageCapabilities capabilities() {
    return StorageCapabilities.multipart(5L << 20, 5L << 30, 10000, true);
  }

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
      if (e.getMessage() != null && e.getMessage().contains("not exist")) return Optional.empty();
      throw fail("MinIO statObject failed", e);
    }
  }

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

  private void check(ObjectLocation l) {
    if (l == null || !id.equals(l.storageId()) || !bucket.equals(l.bucket()))
      throw new IllegalArgumentException("Object belongs to another storage");
  }

  private static MessageDigest sha() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static FileBridgeException fail(String m, Throwable e) {
    if (e instanceof CompletionException && e.getCause() != null) e = e.getCause();
    return new FileBridgeException(FileBridgeErrorCode.STORAGE_FAILURE, m, e);
  }

  private static final class CountingInputStream extends FilterInputStream {
    long count;

    CountingInputStream(InputStream in) {
      super(in);
    }

    public int read() throws IOException {
      int v = super.read();
      if (v >= 0) count++;
      return v;
    }

    public int read(byte[] b, int o, int l) throws IOException {
      int n = super.read(b, o, l);
      if (n > 0) count += n;
      return n;
    }
  }
}
