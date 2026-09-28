package io.github.chansan.filebridge.storage.aliyun;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import io.github.chansan.filebridge.core.error.*;
import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.spi.*;
import java.io.*;
import java.net.URI;
import java.security.*;
import java.time.*;
import java.util.*;

/** 阿里云 OSS 存储适配器。 */
public final class AliyunOssStorageProvider implements MultipartStorageProvider, SignedUrlProvider {
  private final String id, bucket;
  private final OSS client;

  public AliyunOssStorageProvider(String id, String bucket, OSS client) {
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
    return StorageCapabilities.multipart(100 * 1024L, 5L << 30, 10000, true);
  }

  @Override
  public StoredObject write(ObjectWriteRequest r, InputStream input) {
    MessageDigest d = sha();
    Counted in = new Counted(new DigestInputStream(input, d));
    try {
      ObjectMetadata m = new ObjectMetadata();
      if (r.expectedSize() != null) m.setContentLength(r.expectedSize());
      m.setContentType(r.contentType());
      client.putObject(bucket, r.objectKey(), in, m);
      if (r.expectedSize() != null && in.count != r.expectedSize())
        throw new FileBridgeException(
            FileBridgeErrorCode.INVALID_REQUEST, "Received size does not match declared size");
      return new StoredObject(
          new ObjectLocation(id, bucket, r.objectKey()),
          in.count,
          HexFormat.of().formatHex(d.digest()),
          r.contentType(),
          Instant.now());
    } catch (Exception e) {
      throw fail("Aliyun OSS putObject failed", e);
    }
  }

  @Override
  public InputStream open(ObjectLocation l) {
    check(l);
    try {
      return client.getObject(bucket, l.objectKey()).getObjectContent();
    } catch (Exception e) {
      throw fail("Aliyun OSS getObject failed", e);
    }
  }

  @Override
  public Optional<StoredObject> stat(ObjectLocation l) {
    check(l);
    try {
      if (!client.doesObjectExist(bucket, l.objectKey())) return Optional.empty();
      ObjectMetadata m = client.getObjectMetadata(bucket, l.objectKey());
      return Optional.of(
          new StoredObject(
              l, m.getContentLength(), null, m.getContentType(), m.getLastModified().toInstant()));
    } catch (Exception e) {
      throw fail("Aliyun OSS stat failed", e);
    }
  }

  @Override
  public void delete(ObjectLocation l) {
    check(l);
    try {
      client.deleteObject(bucket, l.objectKey());
    } catch (Exception e) {
      throw fail("Aliyun OSS delete failed", e);
    }
  }

  @Override
  public URI createDownloadUrl(ObjectLocation l, Duration v) {
    check(l);
    try {
      return client
          .generatePresignedUrl(bucket, l.objectKey(), Date.from(Instant.now().plus(v)))
          .toURI();
    } catch (Exception e) {
      throw fail("Aliyun OSS presign failed", e);
    }
  }

  @Override
  public MultipartUploadHandle initiateMultipart(String key, String type) {
    try {
      ObjectMetadata m = new ObjectMetadata();
      m.setContentType(type);
      return new MultipartUploadHandle(
          client
              .initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket, key, m))
              .getUploadId(),
          key);
    } catch (Exception e) {
      throw fail("Aliyun OSS multipart initialization failed", e);
    }
  }

  @Override
  public UploadedPart uploadPart(MultipartUploadHandle h, int n, long length, InputStream input) {
    MessageDigest d = sha();
    Counted in = new Counted(new DigestInputStream(input, d));
    try {
      UploadPartRequest r = new UploadPartRequest();
      r.setBucketName(bucket);
      r.setKey(h.objectKey());
      r.setUploadId(h.providerUploadId());
      r.setPartNumber(n);
      r.setPartSize(length);
      r.setInputStream(in);
      // 平台 ETag 只作为完成分片所需标签保存，不能当作文件内容摘要。
      PartETag tag = client.uploadPart(r).getPartETag();
      if (in.count != length)
        throw new FileBridgeException(FileBridgeErrorCode.INVALID_PART, "Part length mismatch");
      return new UploadedPart(n, length, HexFormat.of().formatHex(d.digest()), tag.getETag());
    } catch (Exception e) {
      throw fail("Aliyun OSS uploadPart failed", e);
    }
  }

  @Override
  public List<UploadedPart> listParts(MultipartUploadHandle h) {
    try {
      List<UploadedPart> out = new ArrayList<>();
      Integer marker = null;
      do {
        ListPartsRequest r = new ListPartsRequest(bucket, h.objectKey(), h.providerUploadId());
        if (marker != null) r.setPartNumberMarker(marker);
        r.setMaxParts(1000);
        PartListing p = client.listParts(r);
        for (PartSummary s : p.getParts())
          out.add(new UploadedPart(s.getPartNumber(), s.getSize(), null, s.getETag()));
        marker = p.isTruncated() ? p.getNextPartNumberMarker() : null;
      } while (marker != null);
      return out;
    } catch (Exception e) {
      throw fail("Aliyun OSS listParts failed", e);
    }
  }

  @Override
  public StoredObject completeMultipart(
      MultipartUploadHandle h, List<UploadedPart> parts, String type) {
    try {
      List<PartETag> tags =
          parts.stream()
              .sorted(Comparator.comparingInt(UploadedPart::partNumber))
              .map(p -> new PartETag(p.partNumber(), p.providerPartTag()))
              .toList();
      client.completeMultipartUpload(
          new CompleteMultipartUploadRequest(bucket, h.objectKey(), h.providerUploadId(), tags));
      return stat(new ObjectLocation(id, bucket, h.objectKey())).orElseThrow();
    } catch (Exception e) {
      throw fail("Aliyun OSS completeMultipart failed", e);
    }
  }

  @Override
  public void abortMultipart(MultipartUploadHandle h) {
    try {
      client.abortMultipartUpload(
          new AbortMultipartUploadRequest(bucket, h.objectKey(), h.providerUploadId()));
    } catch (Exception e) {
      throw fail("Aliyun OSS abortMultipart failed", e);
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
    return e instanceof FileBridgeException f
        ? f
        : new FileBridgeException(FileBridgeErrorCode.STORAGE_FAILURE, m, e);
  }

  private static final class Counted extends FilterInputStream {
    long count;

    Counted(InputStream i) {
      super(i);
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
