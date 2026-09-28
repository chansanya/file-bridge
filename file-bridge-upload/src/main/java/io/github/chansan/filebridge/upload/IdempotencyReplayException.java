package io.github.chansan.filebridge.upload;

/** 内部控制流异常，用于回滚并发请求创建的临时业务数据并返回先完成请求的结果。 */
final class IdempotencyReplayException extends RuntimeException {
  private final String responseValue;

  IdempotencyReplayException(String responseValue) {
    super("Idempotent request already completed");
    this.responseValue = responseValue;
  }

  String responseValue() {
    return responseValue;
  }
}
