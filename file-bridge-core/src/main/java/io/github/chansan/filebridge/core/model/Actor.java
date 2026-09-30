package io.github.chansan.filebridge.core.model;

import java.util.Map;

/**
 * 可信业务身份。
 *
 * @param tenantId 租户 ID
 * @param ownerId 用户 ID
 * @param attributes 宿主扩展身份属性
 */
public final class Actor {
  private final String tenantId;
  private final String ownerId;
  private final Map<String, String> attributes;

  public Actor(String tenantId, String ownerId, Map<String, String> attributes) {
    tenantId = require(tenantId, "tenantId");
    ownerId = require(ownerId, "ownerId");
    attributes =
        attributes == null
            ? java.util.Collections.<String, String>emptyMap()
            : java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<String, String>(attributes));

    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.attributes = attributes;
  }

  public String tenantId() {
    return tenantId;
  }

  public String ownerId() {
    return ownerId;
  }

  public Map<String, String> attributes() {
    return attributes;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof Actor)) return false;
    Actor other = (Actor) value;
    return java.util.Objects.equals(tenantId, other.tenantId)
        && java.util.Objects.equals(ownerId, other.ownerId)
        && java.util.Objects.equals(attributes, other.attributes);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(tenantId, ownerId, attributes);
  }

  @Override
  public String toString() {
    return "Actor{"
        + "tenantId="
        + tenantId
        + ", ownerId="
        + ownerId
        + ", attributes="
        + attributes
        + "}";
  }

  /**
   * 校验身份字段并返回原始值。
   *
   * @param value 待校验的字段值
   * @param name 字段名称
   * @return 非空白的字段值
   */
  private static String require(String value, String name) {
    if (value == null || value.trim().isEmpty())
      throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
