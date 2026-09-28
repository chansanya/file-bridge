package io.github.chansan.filebridge.autoconfigure;

import io.github.chansan.filebridge.core.model.DeduplicationScope;
import io.github.chansan.filebridge.core.repository.*;
import io.github.chansan.filebridge.core.service.*;
import io.github.chansan.filebridge.core.spi.*;
import io.github.chansan.filebridge.persistence.jdbc.*;
import io.github.chansan.filebridge.storage.local.*;
import io.github.chansan.filebridge.upload.*;
import java.nio.file.Path;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** FileBridge 核心 Spring Boot 自动配置。 */
@AutoConfiguration
@EnableConfigurationProperties(FileBridgeProperties.class)
@ConditionalOnProperty(prefix = "file-bridge", name = "enabled", matchIfMissing = true)
public class FileBridgeAutoConfiguration {
  @Bean(name = "fileBridgeStorageProviders")
  @ConditionalOnMissingBean(name = "fileBridgeStorageProviders")
  List<StorageProvider> fileBridgeStorageProviders(FileBridgeProperties p) {
    List<StorageProvider> values = new ArrayList<>();
    p.getStorages()
        .forEach(
            (id, s) -> {
              if (!s.isEnabled()) return;
              String type = require(s.getType(), "type").toLowerCase(Locale.ROOT);
              if ("local".equals(type)) {
                values.add(
                    new LocalStorageProvider(
                        id,
                        Path.of(require(s.getRootPath(), "root-path")),
                        Path.of(require(s.getTempPath(), "temp-path"))));
                return;
              }
              values.add(createOptionalProvider(type, id, s));
            });
    return values;
  }

  @Bean
  @ConditionalOnMissingBean
  StorageRegistry storageRegistry(
      @Qualifier("fileBridgeStorageProviders") List<StorageProvider> providers) {
    return new DefaultStorageRegistry(providers);
  }

  @Bean
  @ConditionalOnMissingBean
  ObjectKeyGenerator objectKeyGenerator() {
    return new DefaultObjectKeyGenerator();
  }

  @Bean
  @ConditionalOnMissingBean
  ContentTypeDetector contentTypeDetector() {
    return new DefaultContentTypeDetector();
  }

  @Bean
  @ConditionalOnMissingBean
  UploadQuotaPolicy uploadQuotaPolicy(FileBridgeProperties p) {
    return new MaxSizeUploadQuotaPolicy(p.getUpload().getMaxFileSize().toBytes());
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean
  NamedParameterJdbcTemplate fileBridgeJdbc(DataSource d) {
    return new NamedParameterJdbcTemplate(d);
  }

  @Bean
  @ConditionalOnBean(NamedParameterJdbcTemplate.class)
  @ConditionalOnMissingBean
  FileRepository fileRepository(NamedParameterJdbcTemplate j) {
    return new JdbcFileRepository(j);
  }

  @Bean
  @ConditionalOnBean(NamedParameterJdbcTemplate.class)
  @ConditionalOnMissingBean
  UploadRepository uploadRepository(NamedParameterJdbcTemplate j) {
    return new JdbcUploadRepository(j);
  }

  @Bean
  @ConditionalOnBean(NamedParameterJdbcTemplate.class)
  @ConditionalOnMissingBean
  IdempotencyRepository idempotencyRepository(NamedParameterJdbcTemplate j) {
    return new JdbcIdempotencyRepository(j);
  }

  @Bean
  @ConditionalOnBean(PlatformTransactionManager.class)
  @ConditionalOnMissingBean
  TransactionRunner transactionRunner(PlatformTransactionManager t) {
    return new JdbcTransactionRunner(new TransactionTemplate(t));
  }

  @Bean
  @ConditionalOnBean({
    FileRepository.class,
    IdempotencyRepository.class,
    TransactionRunner.class,
    CurrentActorProvider.class,
    FileAccessPolicy.class
  })
  @ConditionalOnMissingBean
  FileService fileService(
      FileRepository f,
      IdempotencyRepository i,
      TransactionRunner t,
      StorageRegistry s,
      FileBridgeProperties p,
      CurrentActorProvider a,
      FileAccessPolicy policy,
      UploadQuotaPolicy q,
      ObjectKeyGenerator k,
      ContentTypeDetector c) {
    return new DefaultFileService(f, i, t, s, p.getDefaultStorage(), a, policy, q, k, c);
  }

  @Bean
  @ConditionalOnBean({
    UploadRepository.class,
    FileRepository.class,
    IdempotencyRepository.class,
    TransactionRunner.class,
    CurrentActorProvider.class,
    FileAccessPolicy.class
  })
  @ConditionalOnMissingBean
  UploadService uploadService(
      UploadRepository u,
      FileRepository f,
      IdempotencyRepository i,
      TransactionRunner t,
      StorageRegistry s,
      FileBridgeProperties p,
      CurrentActorProvider a,
      FileAccessPolicy policy,
      UploadQuotaPolicy q,
      ObjectKeyGenerator k) {
    DeduplicationScope scope =
        p.getDeduplication().isEnabled()
            ? p.getDeduplication().getScope()
            : DeduplicationScope.DISABLED;
    return new DefaultUploadService(
        u,
        f,
        i,
        t,
        s,
        p.getDefaultStorage(),
        a,
        policy,
        q,
        k,
        scope,
        p.getUpload().getPreferredPartSize().toBytes(),
        p.getUpload().getTaskTtl(),
        p.getUpload().getLeaseDuration(),
        UUID.randomUUID().toString());
  }

  @Bean
  @ConditionalOnBean({UploadRepository.class, FileRepository.class, StorageRegistry.class})
  @ConditionalOnMissingBean
  CleanupService cleanupService(
      UploadRepository u, FileRepository f, StorageRegistry s, FileBridgeProperties p) {
    return new DefaultCleanupService(u, f, s, p.getCleanup().getUnreferencedRetention());
  }

  @Bean
  @ConditionalOnBean({FileRepository.class, StorageRegistry.class})
  @ConditionalOnMissingBean
  ReconciliationService reconciliationService(FileRepository f, StorageRegistry s) {
    return new DefaultReconciliationService(f, s);
  }

  @Bean
  ApplicationRunner fileBridgeValidation(
      FileBridgeProperties p,
      StorageRegistry r,
      org.springframework.beans.factory.ObjectProvider<FileRepository> files,
      org.springframework.beans.factory.ObjectProvider<CurrentActorProvider> actors,
      org.springframework.beans.factory.ObjectProvider<FileAccessPolicy> policies) {
    return args -> {
      r.require(p.getDefaultStorage());
      if (files.getIfAvailable() != null
          && (actors.getIfAvailable() == null || policies.getIfAvailable() == null))
        throw new IllegalStateException(
            "FileBridge business services require CurrentActorProvider and FileAccessPolicy; no"
                + " permissive production default is provided");
    };
  }

  private static StorageProvider createOptionalProvider(
      String type, String id, FileBridgeProperties.Storage s) {
    String className =
        switch (type) {
          case "minio" -> "io.github.chansan.filebridge.storage.minio.MinioStorageFactory";
          case "aliyun", "aliyun-oss" ->
              "io.github.chansan.filebridge.storage.aliyun.AliyunOssStorageFactory";
          case "tencent", "tencent-cos" ->
              "io.github.chansan.filebridge.storage.tencent.TencentCosStorageFactory";
          default -> throw new IllegalStateException("Unsupported storage type: " + type);
        };
    try {
      Class<?> factory = Class.forName(className);
      Map<String, String> values = new HashMap<>();
      put(values, "endpoint", s.getEndpoint());
      put(values, "region", s.getRegion());
      put(values, "bucket", s.getBucket());
      put(values, "accessKey", s.getAccessKey());
      put(values, "secretKey", s.getSecretKey());
      return (StorageProvider)
          factory.getMethod("create", String.class, Map.class).invoke(null, id, values);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Storage module is not on the classpath for type: " + type, e);
    } catch (ReflectiveOperationException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      throw new IllegalStateException("Failed to create storage: " + id, cause);
    }
  }

  private static void put(Map<String, String> values, String key, String value) {
    if (value != null) values.put(key, value);
  }

  private static String require(String v, String name) {
    if (v == null || v.isBlank()) throw new IllegalStateException("Missing storage " + name);
    return v;
  }
}
