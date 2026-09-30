package io.github.chansan.filebridge.core.util;

import java.text.MessageFormat;

/** Minimal Java 8 logging facade used by modules that do not depend on SLF4J. */
public final class BridgeLog {
  private BridgeLog() {}

  public enum Level {
    DEBUG,
    INFO,
    WARNING,
    ERROR
  }

  public static Logger getLogger(String name) {
    return new Logger(java.util.logging.Logger.getLogger(name));
  }

  public static final class Logger {
    private final java.util.logging.Logger delegate;

    private Logger(java.util.logging.Logger delegate) {
      this.delegate = delegate;
    }

    public void log(Level level, String message) {
      delegate.log(toJavaLevel(level), message);
    }

    public void log(Level level, String pattern, Object... arguments) {
      delegate.log(toJavaLevel(level), MessageFormat.format(pattern, arguments));
    }

    public void log(Level level, String message, Throwable error) {
      delegate.log(toJavaLevel(level), message, error);
    }

    private static java.util.logging.Level toJavaLevel(Level level) {
      switch (level) {
        case DEBUG:
          return java.util.logging.Level.FINE;
        case INFO:
          return java.util.logging.Level.INFO;
        case WARNING:
          return java.util.logging.Level.WARNING;
        case ERROR:
          return java.util.logging.Level.SEVERE;
        default:
          return java.util.logging.Level.INFO;
      }
    }
  }
}
