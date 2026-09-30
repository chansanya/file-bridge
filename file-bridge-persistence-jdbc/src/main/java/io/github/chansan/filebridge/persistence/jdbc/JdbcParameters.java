package io.github.chansan.filebridge.persistence.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

/** Java 8 compatible named JDBC parameter map builder. */
final class JdbcParameters {
  private JdbcParameters() {}

  static Map<String, Object> of(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("keyValues must contain key/value pairs");
    }
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      values.put((String) keyValues[i], keyValues[i + 1]);
    }
    return values;
  }
}
