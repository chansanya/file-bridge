package io.github.chansan.filebridge.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Java 8 compatible bounded stream copy helpers. */
public final class IoUtils {
  private static final int BUFFER_SIZE = 16 * 1024;

  private IoUtils() {}

  public static long copy(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long count = 0;
    int read;
    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
      count += read;
    }
    return count;
  }

  public static byte[] readAllBytes(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    copy(input, output);
    return output.toByteArray();
  }
}
