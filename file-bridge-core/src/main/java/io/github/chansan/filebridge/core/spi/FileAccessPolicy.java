package io.github.chansan.filebridge.core.spi;

import io.github.chansan.filebridge.core.model.Actor;
import io.github.chansan.filebridge.core.model.FileReference;
import io.github.chansan.filebridge.core.model.UploadTask;

/** 由宿主应用实现的文件访问授权策略。 */
public interface FileAccessPolicy {
  /**
   * 校验当前身份是否可以上传到指定业务上下文。
   *
   * @param actor 当前可信身份
   * @param businessType 业务类型，可为空
   * @param businessId 业务标识，可为空
   */
  void checkUpload(Actor actor, String businessType, String businessId);

  /**
   * 校验文件读取权限。
   *
   * @param actor 当前可信身份
   * @param reference 待读取业务引用
   */
  void checkRead(Actor actor, FileReference reference);

  /**
   * 校验文件删除权限。
   *
   * @param actor 当前可信身份
   * @param reference 待删除业务引用
   */
  void checkDelete(Actor actor, FileReference reference);

  /**
   * 校验上传任务操作权限。
   *
   * @param actor 当前可信身份
   * @param task 上传任务
   */
  void checkUploadTask(Actor actor, UploadTask task);

  /**
   * 判断当前身份是否可以复用候选文件对象。
   *
   * @param actor 当前可信身份
   * @param candidate 候选业务引用
   * @return 允许创建新引用时返回 {@code true}
   */
  boolean canReuse(Actor actor, FileReference candidate);
}
