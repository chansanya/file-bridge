package io.github.chansan.filebridge.storage.local;

import static org.assertj.core.api.Assertions.*;

import io.github.chansan.filebridge.core.model.ObjectLocation;
import io.github.chansan.filebridge.core.spi.*;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageProviderTest {
  @TempDir Path temp;

  @Test
  void writesReadsAndDeletesWithoutTrustingOriginalNames() throws Exception {
    LocalStorageProvider provider =
        new LocalStorageProvider("local", temp.resolve("data"), temp.resolve("tmp"));
    var stored =
        provider.write(
            new ObjectWriteRequest("2026/09/id", 5L, "text/plain"),
            new ByteArrayInputStream("hello".getBytes()));
    assertThat(stored.sha256()).hasSize(64);
    try (var in = provider.open(stored.location())) {
      assertThat(in.readAllBytes()).isEqualTo("hello".getBytes());
    }
    provider.delete(stored.location());
    assertThat(provider.stat(stored.location())).isEmpty();
  }

  @Test
  void rejectsTraversal() {
    LocalStorageProvider provider =
        new LocalStorageProvider("local", temp.resolve("data"), temp.resolve("tmp"));
    assertThatThrownBy(() -> provider.stat(new ObjectLocation("local", null, "../escape")))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void rejectsSymbolicLinkEscape() throws Exception {
    Path data = temp.resolve("data");
    Path outside = temp.resolve("outside");
    Files.createDirectories(data);
    Files.createDirectories(outside);
    Files.createSymbolicLink(data.resolve("escape"), outside);
    LocalStorageProvider provider = new LocalStorageProvider("local", data, temp.resolve("tmp"));

    assertThatThrownBy(
            () ->
                provider.write(
                    new ObjectWriteRequest("escape/file", 5L, "text/plain"),
                    new ByteArrayInputStream("hello".getBytes())))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void repairsMissingPartMetadataOnIdempotentRetry() throws Exception {
    Path temporary = temp.resolve("tmp");
    LocalStorageProvider provider =
        new LocalStorageProvider("local", temp.resolve("data"), temporary);
    MultipartUploadHandle handle = provider.initiateMultipart("target", "text/plain");
    byte[] content = "hello".getBytes();
    provider.uploadPart(handle, 1, content.length, new ByteArrayInputStream(content));
    Files.delete(
        temporary
            .resolve("multipart")
            .resolve(handle.providerUploadId())
            .resolve("parts")
            .resolve("1.properties"));

    UploadedPart retried =
        provider.uploadPart(handle, 1, content.length, new ByteArrayInputStream(content));

    assertThat(retried.size()).isEqualTo(content.length);
    assertThat(retried.sha256()).hasSize(64);
  }
}
