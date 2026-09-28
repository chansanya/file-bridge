package io.github.chansan.filebridge.core.spi;

import io.github.chansan.filebridge.core.model.Actor;

/** 生成不可由客户端控制的物理对象路径。 */
public interface ObjectKeyGenerator {
  /**
   * 生成新的对象路径。
   *
   * @param actor 当前可信身份
   * @param originalName 原始文件名，仅可用于辅助分类，不能直接拼接路径
   * @return 存储内唯一对象路径
   */
  String generate(Actor actor, String originalName);
}
