package io.github.chansan.filebridge.storage.minio;

import static org.assertj.core.api.Assertions.*;

import io.github.chansan.filebridge.core.model.*;
import io.github.chansan.filebridge.core.spi.*;
import io.minio.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@EnabledIfEnvironmentVariable(named = "FILE_BRIDGE_MINIO_TEST", matches = "true")
@Testcontainers
class MinioStorageProviderTest {
  private static final String ACCESS_KEY = "minioadmin";
  private static final String SECRET_KEY = "minioadmin";
  private static final String BUCKET = "file-bridge-test";

  @Container
  static final GenericContainer<?> MINIO =
      new GenericContainer<>("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")
          .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
          .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
          .withCommand("server", "/data")
          .withExposedPorts(9000);

  private MinioStorageProvider provider;

  @BeforeEach
  void setUp() {
    String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    MinioAsyncClient client =
        MinioAsyncClient.builder().endpoint(endpoint).credentials(ACCESS_KEY, SECRET_KEY).build();
    client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build()).join();
    provider = new MinioStorageProvider("minio-test", BUCKET, client);
  }

  @AfterEach
  void tearDown() throws Exception {
    provider.close();
  }

  @Test
  void writesReadsAndDeletesObject() throws Exception {
    byte[] content = "hello-minio".getBytes();
    StoredObject stored =
        provider.write(
            new ObjectWriteRequest("objects/test", (long) content.length, "text/plain"),
            new ByteArrayInputStream(content));

    assertThat(provider.stat(stored.location())).isPresent();
    try (InputStream input = provider.open(stored.location())) {
      assertThat(io.github.chansan.filebridge.core.util.IoUtils.readAllBytes(input))
          .isEqualTo(content);
    }
    provider.delete(stored.location());
    assertThat(provider.stat(stored.location())).isEmpty();
  }

  @Test
  void completesNativeMultipartUpload() throws Exception {
    byte[] first = new byte[5 * 1024 * 1024];
    byte[] last = "tail".getBytes();
    MultipartUploadHandle handle =
        provider.initiateMultipart("objects/multipart", "application/octet-stream");
    UploadedPart part1 =
        provider.uploadPart(handle, 1, first.length, new ByteArrayInputStream(first));
    UploadedPart part2 =
        provider.uploadPart(handle, 2, last.length, new ByteArrayInputStream(last));

    StoredObject completed =
        provider.completeMultipart(
            handle, java.util.Arrays.asList(part1, part2), "application/octet-stream");

    assertThat(completed.size()).isEqualTo(first.length + last.length);
  }
}
