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
public final class AliyunOssStorageProvider
    implements MultipartStorageProvider, SignedUrlProvider, AutoCloseable {
  private final String id, bucket;
  private final OSS client;

  /**
   * 创建阿里云 OSS 存储适配器。
   *
   * @param id 存储实例 ID
   * @param bucket Bucket 名称
   * @param client 阿里云 OSS 客户端
   */
  public AliyunOssStorageProvider(String id, String bucket, OSS client) {
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
   * @return 阿里云 OSS 能力和分片限制
   */
  @Override
  public StorageCapabilities capabilities() {
    return StorageCapabilities.multipart(100 * 1024L, 5L << 30, 10000, true);
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
      return client.getObject(bucket, l.objectKey()).getObjectContent();
    } catch (Exception e) {
      throw fail("Aliyun OSS getObject failed", e);
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
      if (!client.doesObjectExist(bucket, l.objectKey())) return Optional.empty();
      ObjectMetadata m = client.getObjectMetadata(bucket, l.objectKey());
      return Optional.of(
          new StoredObject(
              l, m.getContentLength(), null, m.getContentType(), m.getLastModified().toInstant()));
    } catch (Exception e) {
      throw fail("Aliyun OSS stat failed", e);
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
      client.deleteObject(bucket, l.objectKey());
    } catch (Exception e) {
      throw fail("Aliyun OSS delete failed", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param l 可信的对象定位信息
   * @param v 地址有效时长
   * @return 临时下载 URI
   */
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

  /**
   * {@inheritDoc}
   *
   * @param h 平台上传句柄
   * @param n 分片序号
   * @param length 分片预期字节数
   * @param input 分片内容输入流
   * @return 服务端确认的分片信息
   */
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

  /**
   * {@inheritDoc}
   *
   * @param h 平台上传句柄
   * @return 服务端已存分片列表
   */
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

  /**
   * {@inheritDoc}
   *
   * @param h 平台上传句柄
   */
  @Override
  public void abortMultipart(MultipartUploadHandle h) {
    try {
      client.abortMultipartUpload(
          new AbortMultipartUploadRequest(bucket, h.objectKey(), h.providerUploadId()));
    } catch (Exception e) {
      throw fail("Aliyun OSS abortMultipart failed", e);
    }
  }

  /**
   * 校验对象定位信息归属。
   *
   * @param l 待校验定位信息
   */
  @Override
  public void close() throws Exception {
    client.shutdown();
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
   * 保留领域异常并包装存储失败。
   *
   * @param m 可读错误信息
   * @param e 原始异常
   * @return 领域异常
   */
  private static FileBridgeException fail(String m, Throwable e) {
    return e instanceof FileBridgeException f
        ? f
        : new FileBridgeException(FileBridgeErrorCode.STORAGE_FAILURE, m, e);
  }

  private static final class Counted extends FilterInputStream {
    long count;

    /**
     * 包装待计数的输入流。
     *
     * @param i 原始输入流
     */
    Counted(InputStream i) {
      super(i);
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
