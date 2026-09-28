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

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean v) {
    enabled = v;
  }

  public String getDefaultStorage() {
    return defaultStorage;
  }

  public void setDefaultStorage(String v) {
    defaultStorage = v;
  }

  public Upload getUpload() {
    return upload;
  }

  public void setUpload(Upload v) {
    upload = v;
  }

  public Cleanup getCleanup() {
    return cleanup;
  }

  public void setCleanup(Cleanup v) {
    cleanup = v;
  }

  public Deduplication getDeduplication() {
    return deduplication;
  }

  public void setDeduplication(Deduplication v) {
    deduplication = v;
  }

  public Map<String, Storage> getStorages() {
    return storages;
  }

  public void setStorages(Map<String, Storage> v) {
    storages = v;
  }

  public static class Upload {
    private DataSize maxFileSize = DataSize.ofGigabytes(2);
    private DataSize preferredPartSize = DataSize.ofMegabytes(8);
    private Duration taskTtl = Duration.ofHours(24);
    private Duration leaseDuration = Duration.ofMinutes(10);

    public DataSize getMaxFileSize() {
      return maxFileSize;
    }

    public void setMaxFileSize(DataSize v) {
      maxFileSize = v;
    }

    public DataSize getPreferredPartSize() {
      return preferredPartSize;
    }

    public void setPreferredPartSize(DataSize v) {
      preferredPartSize = v;
    }

    public Duration getTaskTtl() {
      return taskTtl;
    }

    public void setTaskTtl(Duration v) {
      taskTtl = v;
    }

    public Duration getLeaseDuration() {
      return leaseDuration;
    }

    public void setLeaseDuration(Duration v) {
      leaseDuration = v;
    }
  }

  public static class Cleanup {
    private boolean enabled = true;
    private Duration interval = Duration.ofMinutes(30);
    private Duration unreferencedRetention = Duration.ofHours(24);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean v) {
      enabled = v;
    }

    public Duration getInterval() {
      return interval;
    }

    public void setInterval(Duration v) {
      interval = v;
    }

    public Duration getUnreferencedRetention() {
      return unreferencedRetention;
    }

    public void setUnreferencedRetention(Duration v) {
      unreferencedRetention = v;
    }
  }

  public static class Deduplication {
    private boolean enabled = true;
    private DeduplicationScope scope = DeduplicationScope.USER;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean v) {
      enabled = v;
    }

    public DeduplicationScope getScope() {
      return scope;
    }

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

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean v) {
      enabled = v;
    }

    public String getType() {
      return type;
    }

    public void setType(String v) {
      type = v;
    }

    public String getRootPath() {
      return rootPath;
    }

    public void setRootPath(String v) {
      rootPath = v;
    }

    public String getTempPath() {
      return tempPath;
    }

    public void setTempPath(String v) {
      tempPath = v;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String v) {
      endpoint = v;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String v) {
      region = v;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(String v) {
      bucket = v;
    }

    public String getAccessKey() {
      return accessKey;
    }

    public void setAccessKey(String v) {
      accessKey = v;
    }

    public String getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(String v) {
      secretKey = v;
    }
  }
}
