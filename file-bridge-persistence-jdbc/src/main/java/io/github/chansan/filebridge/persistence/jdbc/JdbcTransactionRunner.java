package io.github.chansan.filebridge.persistence.jdbc;

import io.github.chansan.filebridge.core.spi.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** 基于 Spring TransactionTemplate 的事务端口实现。 */
public final class JdbcTransactionRunner implements TransactionRunner {
  private final TransactionTemplate template;

  public JdbcTransactionRunner(TransactionTemplate template) {
    this.template = template;
  }

  @Override
  public <T> T required(Supplier<T> work) {
    return template.execute(status -> work.get());
  }
}
