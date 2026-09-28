package io.github.chansan.filebridge.core.model;

import java.util.Map;

/**
 * 可信业务身份。
 *
 * @param tenantId 租户 ID
 * @param ownerId 用户 ID
 * @param attributes 宿主扩展身份属性
 */
public record Actor(String tenantId, String ownerId, Map<String, String> attributes) {
  public Actor {
    tenantId = require(tenantId, "tenantId");
    ownerId = require(ownerId, "ownerId");
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }

  /**
   * 校验身份字段并返回原始值。
   *
   * @param value 待校验的字段值
   * @param name 字段名称
   * @return 非空白的字段值
   */
  private static String require(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
