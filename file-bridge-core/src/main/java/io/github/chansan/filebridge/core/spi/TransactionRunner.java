package io.github.chansan.filebridge.core.spi;

import java.util.function.Supplier;

/** 隔离核心业务与具体事务框架。 */
public interface TransactionRunner {
  /**
   * 在必需事务中执行并返回结果。
   *
   * @param work 事务内工作
   * @param <T> 返回值类型
   * @return 事务成功提交后的结果
   */
  <T> T required(Supplier<T> work);

  /**
   * 在必需事务中执行无返回值工作。
   *
   * @param work 事务内工作
   */
  default void required(Runnable work) {
    required(
        () -> {
          work.run();
          return null;
        });
  }
}
