package io.github.chansan.filebridge.upload;

import io.github.chansan.filebridge.core.model.Actor;
import io.github.chansan.filebridge.core.spi.ObjectKeyGenerator;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** 使用 UTC 日期和随机 UUID 生成对象路径。 */
public final class DefaultObjectKeyGenerator implements ObjectKeyGenerator {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("uuuu/MM/dd").withZone(ZoneOffset.UTC);

  @Override
  public String generate(Actor actor, String originalName) {
    return DATE.format(Instant.now()) + "/" + UUID.randomUUID();
  }
}
