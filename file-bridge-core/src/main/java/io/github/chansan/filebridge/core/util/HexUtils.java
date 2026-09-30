package io.github.chansan.filebridge.core.util;

/** Java 8 compatible hexadecimal encoding helpers. */
public final class HexUtils {
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private HexUtils() {}

  public static String toHex(byte[] bytes) {
    char[] chars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xff;
      chars[i * 2] = HEX[value >>> 4];
      chars[i * 2 + 1] = HEX[value & 0x0f];
    }
    return new String(chars);
  }
}
