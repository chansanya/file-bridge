package io.github.chansan.filebridge.core.model;

/** 对账问题处理状态。 */
public enum ReconciliationIssueStatus {
  OPEN,
  PROCESSING,
  RETRY_WAIT,
  RESOLVED,
  MANUAL_REQUIRED
}
