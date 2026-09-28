package io.github.chansan.filebridge.core.model;

/** 物理对象生命周期状态。 */
public enum StorageObjectStatus {
  PENDING,
  AVAILABLE,
  DELETE_PENDING,
  DELETED,
  ERROR
}
