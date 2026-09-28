package io.github.chansan.filebridge.core.spi;

import io.github.chansan.filebridge.core.model.Actor;

/** 从宿主应用的可信认证上下文中取得当前访问者。 */
public interface CurrentActorProvider {
  /**
   * @return 当前租户和用户身份，不得直接来自未校验的请求参数
   */
  Actor currentActor();
}
