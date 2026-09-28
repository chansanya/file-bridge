package io.github.chansan.filebridge.persistence.jdbc;

import io.github.chansan.filebridge.core.spi.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** 基于 Spring TransactionTemplate 的事务端口实现。 */
public final class JdbcTransactionRunner implements TransactionRunner {
  private final TransactionTemplate template;

  /**
   * 创建 Spring 事务执行器。
   *
   * @param template Spring 事务模板
   */
  public JdbcTransactionRunner(TransactionTemplate template) {
    this.template = template;
  }

  /**
   * {@inheritDoc}
   *
   * @param work 事务内工作
   * @param <T> 返回值类型
   * @return 事务成功提交后的结果
   */
  @Override
  public <T> T required(Supplier<T> work) {
    return template.execute(status -> work.get());
  }
}
