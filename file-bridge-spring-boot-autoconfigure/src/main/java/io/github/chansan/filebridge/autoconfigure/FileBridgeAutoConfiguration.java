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
  /**
   * 根据配置创建全部启用的存储适配器。
   *
   * @param p FileBridge 配置属性
   * @return 按配置顺序排列的存储适配器
   */
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

  /**
   * 创建默认存储注册表。
   *
   * @param providers 全部存储适配器
   * @return 存储注册表
   */
  @Bean
  @ConditionalOnMissingBean
  StorageRegistry storageRegistry(
      @Qualifier("fileBridgeStorageProviders") List<StorageProvider> providers) {
    return new DefaultStorageRegistry(providers);
  }

  /**
   * 创建默认对象路径生成器。
   *
   * @return 对象路径生成器
   */
  @Bean
  @ConditionalOnMissingBean
  ObjectKeyGenerator objectKeyGenerator() {
    return new DefaultObjectKeyGenerator();
  }

  /**
   * 创建默认内容类型检测器。
   *
   * @return 内容类型检测器
   */
  @Bean
  @ConditionalOnMissingBean
  ContentTypeDetector contentTypeDetector() {
    return new DefaultContentTypeDetector();
  }

  /**
   * 创建基于配置上限的上传配额策略。
   *
   * @param p FileBridge 配置属性
   * @return 上传配额策略
   */
  @Bean
  @ConditionalOnMissingBean
  UploadQuotaPolicy uploadQuotaPolicy(FileBridgeProperties p) {
    return new MaxSizeUploadQuotaPolicy(p.getUpload().getMaxFileSize().toBytes());
  }

  /**
   * 创建 FileBridge 使用的具名参数 JDBC 模板。
   *
   * @param d 应用数据源
   * @return 具名参数 JDBC 模板
   */
  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean
  NamedParameterJdbcTemplate fileBridgeJdbc(DataSource d) {
    return new NamedParameterJdbcTemplate(d);
  }

  /**
   * 创建 JDBC 文件仓储。
   *
   * @param j 具名参数 JDBC 模板
   * @return 文件仓储
   */
  @Bean
  @ConditionalOnBean(NamedParameterJdbcTemplate.class)
  @ConditionalOnMissingBean
  FileRepository fileRepository(NamedParameterJdbcTemplate j) {
    return new JdbcFileRepository(j);
  }

  /**
   * 创建 JDBC 上传任务仓储。
   *
   * @param j 具名参数 JDBC 模板
   * @return 上传任务仓储
   */
  @Bean
  @ConditionalOnBean(NamedParameterJdbcTemplate.class)
  @ConditionalOnMissingBean
  UploadRepository uploadRepository(NamedParameterJdbcTemplate j) {
    return new JdbcUploadRepository(j);
  }

  /**
   * 创建 JDBC 幂等记录仓储。
   *
   * @param j 具名参数 JDBC 模板
   * @return 幂等记录仓储
   */
  @Bean
  @ConditionalOnBean(NamedParameterJdbcTemplate.class)
  @ConditionalOnMissingBean
  IdempotencyRepository idempotencyRepository(NamedParameterJdbcTemplate j) {
    return new JdbcIdempotencyRepository(j);
  }

  /**
   * 创建基于 Spring 事务管理器的必需事务执行器。
   *
   * @param t 平台事务管理器
   * @return 事务执行器
   */
  @Bean
  @ConditionalOnBean(PlatformTransactionManager.class)
  @ConditionalOnMissingBean
  TransactionRunner transactionRunner(PlatformTransactionManager t) {
    return new JdbcTransactionRunner(new TransactionTemplate(t));
  }

  /**
   * 创建默认文件业务服务。
   *
   * @param f 文件仓储
   * @param i 幂等记录仓储
   * @param t 事务执行器
   * @param s 存储注册表
   * @param p FileBridge 配置属性
   * @param a 当前可信身份提供器
   * @param policy 文件访问策略
   * @param q 上传配额策略
   * @param k 对象路径生成器
   * @param c 内容类型检测器
   * @return 文件业务服务
   */
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

  /**
   * 创建默认分片上传服务。
   *
   * @param u 上传任务仓储
   * @param f 文件仓储
   * @param i 幂等记录仓储
   * @param t 事务执行器
   * @param s 存储注册表
   * @param p FileBridge 配置属性
   * @param a 当前可信身份提供器
   * @param policy 文件访问策略
   * @param q 上传配额策略
   * @param k 对象路径生成器
   * @return 分片上传服务
   */
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

  /**
   * 创建默认清理服务。
   *
   * @param u 上传任务仓储
   * @param f 文件仓储
   * @param s 存储注册表
   * @param p FileBridge 配置属性
   * @return 清理服务
   */
  @Bean
  @ConditionalOnBean({UploadRepository.class, FileRepository.class, StorageRegistry.class})
  @ConditionalOnMissingBean
  CleanupService cleanupService(
      UploadRepository u, FileRepository f, StorageRegistry s, FileBridgeProperties p) {
    return new DefaultCleanupService(u, f, s, p.getCleanup().getUnreferencedRetention());
  }

  /**
   * 创建默认对象对账服务。
   *
   * @param f 文件仓储
   * @param s 存储注册表
   * @return 对账服务
   */
  @Bean
  @ConditionalOnBean({FileRepository.class, StorageRegistry.class})
  @ConditionalOnMissingBean
  ReconciliationService reconciliationService(FileRepository f, StorageRegistry s) {
    return new DefaultReconciliationService(f, s);
  }

  /**
   * 创建应用启动时的配置校验器。
   *
   * @param p FileBridge 配置属性
   * @param r 存储注册表
   * @param files 可选文件仓储
   * @param actors 可选当前身份提供器
   * @param policies 可选文件访问策略
   * @return 应用启动校验器
   */
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

  /**
   * 通过可选存储模块反射创建云存储适配器。
   *
   * @param type 存储类型
   * @param id 存储实例 ID
   * @param s 存储实例配置
   * @return 云存储适配器
   */
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

  /**
   * 将非空配置值写入适配器配置。
   *
   * @param values 目标配置集合
   * @param key 配置键
   * @param value 配置值，可为空
   */
  private static void put(Map<String, String> values, String key, String value) {
    if (value != null) values.put(key, value);
  }

  /**
   * 校验必需存储配置。
   *
   * @param v 配置值
   * @param name 配置名称
   * @return 非空白配置值
   */
  private static String require(String v, String name) {
    if (v == null || v.isBlank()) throw new IllegalStateException("Missing storage " + name);
    return v;
  }
}
