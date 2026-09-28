package io.github.chansan.filebridge.autoconfigure;

import io.github.chansan.filebridge.core.model.DeduplicationScope;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** FileBridge 配置属性。 */
@ConfigurationProperties("file-bridge")
public class FileBridgeProperties {
  private boolean enabled = true;
  private String defaultStorage = "local-main";
  private Upload upload = new Upload();
  private Cleanup cleanup = new Cleanup();
  private Deduplication deduplication = new Deduplication();
  private Map<String, Storage> storages = new LinkedHashMap<>();

    /**
     * 获取是否启用 FileBridge。
     *
     * @return 启用状态
     */
  public boolean isEnabled() {
    return enabled;
  }

    /**
     * 设置是否启用 FileBridge。
     *
     * @param v 启用状态
     */
  public void setEnabled(boolean v) {
    enabled = v;
  }

    /**
     * 获取默认存储实例 ID。
     *
     * @return 默认存储实例 ID
     */
  public String getDefaultStorage() {
    return defaultStorage;
  }

    /**
     * 设置默认存储实例 ID。
     *
     * @param v 默认存储实例 ID
     */
  public void setDefaultStorage(String v) {
    defaultStorage = v;
  }

    /**
     * 获取上传配置。
     *
     * @return 上传配置
     */
  public Upload getUpload() {
    return upload;
  }

    /**
     * 设置上传配置。
     *
     * @param v 上传配置
     */
  public void setUpload(Upload v) {
    upload = v;
  }

    /**
     * 获取清理配置。
     *
     * @return 清理配置
     */
  public Cleanup getCleanup() {
    return cleanup;
  }

    /**
     * 设置清理配置。
     *
     * @param v 清理配置
     */
  public void setCleanup(Cleanup v) {
    cleanup = v;
  }

    /**
     * 获取秒传配置。
     *
     * @return 秒传配置
     */
  public Deduplication getDeduplication() {
    return deduplication;
  }

    /**
     * 设置秒传配置。
     *
     * @param v 秒传配置
     */
  public void setDeduplication(Deduplication v) {
    deduplication = v;
  }

    /**
     * 获取按实例 ID 索引的存储配置。
     *
     * @return 存储配置集合
     */
  public Map<String, Storage> getStorages() {
    return storages;
  }

    /**
     * 设置按实例 ID 索引的存储配置。
     *
     * @param v 存储配置集合
     */
  public void setStorages(Map<String, Storage> v) {
    storages = v;
  }

  public static class Upload {
    private DataSize maxFileSize = DataSize.ofGigabytes(2);
    private DataSize preferredPartSize = DataSize.ofMegabytes(8);
    private Duration taskTtl = Duration.ofHours(24);
    private Duration leaseDuration = Duration.ofMinutes(10);

    /**
     * 获取单文件大小上限。
     *
     * @return 单文件大小上限
     */
    public DataSize getMaxFileSize() {
      return maxFileSize;
    }

    /**
     * 设置单文件大小上限。
     *
     * @param v 单文件大小上限
     */
    public void setMaxFileSize(DataSize v) {
      maxFileSize = v;
    }

    /**
     * 获取推荐分片大小。
     *
     * @return 推荐分片大小
     */
    public DataSize getPreferredPartSize() {
      return preferredPartSize;
    }

    /**
     * 设置推荐分片大小。
     *
     * @param v 推荐分片大小
     */
    public void setPreferredPartSize(DataSize v) {
      preferredPartSize = v;
    }

    /**
     * 获取上传任务保留时长。
     *
     * @return 上传任务保留时长
     */
    public Duration getTaskTtl() {
      return taskTtl;
    }

    /**
     * 设置上传任务保留时长。
     *
     * @param v 上传任务保留时长
     */
    public void setTaskTtl(Duration v) {
      taskTtl = v;
    }

    /**
     * 获取完成处理租约时长。
     *
     * @return 完成处理租约时长
     */
    public Duration getLeaseDuration() {
      return leaseDuration;
    }

    /**
     * 设置完成处理租约时长。
     *
     * @param v 完成处理租约时长
     */
    public void setLeaseDuration(Duration v) {
      leaseDuration = v;
    }
  }

  public static class Cleanup {
    private boolean enabled = true;
    private Duration interval = Duration.ofMinutes(30);
    private Duration unreferencedRetention = Duration.ofHours(24);

    /**
     * 获取是否启用清理任务。
     *
     * @return 清理任务启用状态
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * 设置是否启用清理任务。
     *
     * @param v 清理任务启用状态
     */
    public void setEnabled(boolean v) {
      enabled = v;
    }

    /**
     * 获取清理任务执行间隔。
     *
     * @return 清理任务执行间隔
     */
    public Duration getInterval() {
      return interval;
    }

    /**
     * 设置清理任务执行间隔。
     *
     * @param v 清理任务执行间隔
     */
    public void setInterval(Duration v) {
      interval = v;
    }

    /**
     * 获取无引用对象保留时长。
     *
     * @return 无引用对象保留时长
     */
    public Duration getUnreferencedRetention() {
      return unreferencedRetention;
    }

    /**
     * 设置无引用对象保留时长。
     *
     * @param v 无引用对象保留时长
     */
    public void setUnreferencedRetention(Duration v) {
      unreferencedRetention = v;
    }
  }

  public static class Deduplication {
    private boolean enabled = true;
    private DeduplicationScope scope = DeduplicationScope.USER;

    /**
     * 获取是否启用秒传。
     *
     * @return 秒传启用状态
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * 设置是否启用秒传。
     *
     * @param v 秒传启用状态
     */
    public void setEnabled(boolean v) {
      enabled = v;
    }

    /**
     * 获取秒传授权范围。
     *
     * @return 秒传授权范围
     */
    public DeduplicationScope getScope() {
      return scope;
    }

    /**
     * 设置秒传授权范围。
     *
     * @param v 秒传授权范围
     */
    public void setScope(DeduplicationScope v) {
      scope = v;
    }
  }

  public static class Storage {
    private boolean enabled = true;
    private String type;
    private String rootPath;
    private String tempPath;
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;

    /**
     * 获取是否启用存储实例。
     *
     * @return 存储实例启用状态
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * 设置是否启用存储实例。
     *
     * @param v 存储实例启用状态
     */
    public void setEnabled(boolean v) {
      enabled = v;
    }

    /**
     * 获取存储类型。
     *
     * @return 存储类型
     */
    public String getType() {
      return type;
    }

    /**
     * 设置存储类型。
     *
     * @param v 存储类型
     */
    public void setType(String v) {
      type = v;
    }

    /**
     * 获取本地存储根路径。
     *
     * @return 本地存储根路径
     */
    public String getRootPath() {
      return rootPath;
    }

    /**
     * 设置本地存储根路径。
     *
     * @param v 本地存储根路径
     */
    public void setRootPath(String v) {
      rootPath = v;
    }

    /**
     * 获取本地存储临时路径。
     *
     * @return 本地存储临时路径
     */
    public String getTempPath() {
      return tempPath;
    }

    /**
     * 设置本地存储临时路径。
     *
     * @param v 本地存储临时路径
     */
    public void setTempPath(String v) {
      tempPath = v;
    }

    /**
     * 获取对象存储服务地址。
     *
     * @return 对象存储服务地址
     */
    public String getEndpoint() {
      return endpoint;
    }

    /**
     * 设置对象存储服务地址。
     *
     * @param v 对象存储服务地址
     */
    public void setEndpoint(String v) {
      endpoint = v;
    }

    /**
     * 获取对象存储区域。
     *
     * @return 对象存储区域
     */
    public String getRegion() {
      return region;
    }

    /**
     * 设置对象存储区域。
     *
     * @param v 对象存储区域
     */
    public void setRegion(String v) {
      region = v;
    }

    /**
     * 获取对象存储 Bucket。
     *
     * @return 对象存储 Bucket
     */
    public String getBucket() {
      return bucket;
    }

    /**
     * 设置对象存储 Bucket。
     *
     * @param v 对象存储 Bucket
     */
    public void setBucket(String v) {
      bucket = v;
    }

    /**
     * 获取访问密钥。
     *
     * @return 访问密钥
     */
    public String getAccessKey() {
      return accessKey;
    }

    /**
     * 设置访问密钥。
     *
     * @param v 访问密钥
     */
    public void setAccessKey(String v) {
      accessKey = v;
    }

    /**
     * 获取私有密钥。
     *
     * @return 私有密钥
     */
    public String getSecretKey() {
      return secretKey;
    }

    /**
     * 设置私有密钥。
     *
     * @param v 私有密钥
     */
    public void setSecretKey(String v) {
      secretKey = v;
    }
  }
}
