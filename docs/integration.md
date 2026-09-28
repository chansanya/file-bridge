# FileBridge 接入指南

## 1. 接入条件

| 项目 | 要求 |
| --- | --- |
| Java | 17 或更高版本 |
| Spring Boot | 4.1.x |
| 数据库 | MySQL 8.4 |
| 数据源 | 宿主应用提供 `DataSource` 和事务管理器 |
| 存储 | 至少一个 `StorageProvider` |
| 安全扩展 | `CurrentActorProvider`、`FileAccessPolicy` |

## 2. 引入依赖

### 2.1 Java 服务方式

只使用 Java 服务接口时，引入 Starter：

```xml
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Starter 默认提供：

- 核心领域和上传服务
- JDBC 持久化实现
- 本地文件系统适配器
- Spring Boot 自动配置

### 2.2 REST 接口与独立示例工程

Starter 只提供 Java 服务能力，不包含 Controller、Servlet Filter 或 HTTP 异常映射。

仓库中的 [`example`](../example) 是一个独立 Spring Boot 工程：

- 使用 `spring-boot-starter-parent`，不继承 FileBridge Parent；
- 只依赖公开入口 `file-bridge-spring-boot-starter`；
- 自己定义 REST Controller、错误响应和请求过滤器；
- 模拟真实业务项目如何封装自己的 HTTP API。

`example` 不是供其他项目引入的依赖。接入方如果需要 REST，可以参考其中的 Controller，根据自己的认证、响应格式和接口规范进行实现。

本地运行独立示例前，先将 Starter 安装到本地 Maven 仓库：

```bash
./mvnw install -DskipTests -pl file-bridge-spring-boot-starter -am
./mvnw -f example/pom.xml spring-boot:run
```

## 3. 初始化数据库

数据库脚本位于 [`docs/database`](database/README.md)：

1. `V1__file_objects_and_references.sql`
2. `V2__multipart_upload.sql`
3. `V3__cleanup_and_reconciliation.sql`

必须按版本顺序执行。

Starter 本身：

- 不引入 Flyway；
- 不主动创建或修改数据库表；
- 不删除宿主业务数据。

如果宿主已经使用 Flyway，可以将脚本复制到宿主项目自己的迁移目录，由宿主管理版本和发布时间。组件 JAR 内也包含迁移资源；宿主若扫描默认的 `classpath:db/migration`，应确认不会与现有版本号冲突。

## 4. 配置存储实例

### 4.1 为什么是必需项

MySQL 只保存文件元数据，不保存文件正文。FileBridge 的以下操作都依赖真实存储：

- 普通上传写入文件正文；
- 分片上传保存临时分片并生成最终对象；
- 下载打开对象流；
- 删除和清理移除物理对象；
- 对账确认数据库记录与物理文件一致。

因此，`file-bridge.enabled=true` 时必须满足以下条件：

1. 至少存在一个已启用的存储实例；
2. `default-storage` 必须指向该实例；
3. 历史对象记录中的 `storageId` 必须继续可用。

启动时 FileBridge 会执行默认存储校验。如果 `default-storage` 找不到对应实例，应用将直接启动失败，而不是拖到上传请求发生时再报错。

### 4.2 最小本地配置

本地目录已经构成完整存储实例，不需要额外部署对象存储服务：

```yaml
file-bridge:
  enabled: true
  default-storage: local-main
  storages:
    local-main:
      type: local
      root-path: ./data/files
      temp-path: ./data/uploads
```

`local-main` 是稳定的存储实例 ID，必须与 `default-storage` 完全一致。

生产环境不要随意修改该 ID。数据库中的历史对象会使用原 ID 查找存储适配器。

### 4.3 使用云存储

Starter 不自动引入云 SDK。使用哪个平台，就显式添加对应模块：

```xml
<!-- MinIO -->
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-minio</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>

<!-- 阿里云 OSS -->
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-aliyun</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>

<!-- 腾讯云 COS -->
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-tencent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

完整配置示例见 [配置文档](configuration.md)。

### 4.4 自定义存储实现

宿主可以实现 `StorageProvider` 或 `MultipartStorageProvider`，并自行提供 `StorageRegistry` Bean：

```java
@Bean
StorageRegistry storageRegistry(MyStorageProvider provider) {
  return new DefaultStorageRegistry(List.of(provider));
}
```

即使使用自定义 Bean，注册表中仍必须存在 `default-storage` 指定的实例。

## 5. 提供可信身份

FileBridge 不接受请求参数中的 `tenantId` 或 `ownerId` 作为可信身份。宿主必须从已认证上下文中读取身份：

```java
@Bean
CurrentActorProvider currentActorProvider() {
  return () -> {
    // 示例：实际项目应从 Spring Security、网关透传凭证或内部认证上下文读取。
    String tenantId = TrustedSecurityContext.requireTenantId();
    String ownerId = TrustedSecurityContext.requireUserId();
    return new Actor(tenantId, ownerId, Map.of());
  };
}
```

禁止直接采用以下做法：

```java
// 错误示例：请求头可以被客户端伪造。
String ownerId = request.getHeader("X-User-Id");
```

除非该请求头已经由可信网关签名、校验，并且应用明确阻止客户端绕过网关访问。

## 6. 提供访问策略

生产环境必须提供 `FileAccessPolicy` Bean。组件不会默认“全部允许”：

```java
@Bean
FileAccessPolicy fileAccessPolicy() {
  return new FileAccessPolicy() {
    @Override
    public void checkUpload(Actor actor, String businessType, String businessId) {
      // 校验当前身份是否允许向该业务上下文上传。
    }

    @Override
    public void checkRead(Actor actor, FileReference reference) {
      requireOwner(actor, reference.tenantId(), reference.ownerId());
    }

    @Override
    public void checkDelete(Actor actor, FileReference reference) {
      requireOwner(actor, reference.tenantId(), reference.ownerId());
    }

    @Override
    public void checkUploadTask(Actor actor, UploadTask task) {
      requireOwner(actor, task.tenantId(), task.ownerId());
    }

    @Override
    public boolean canReuse(Actor actor, FileReference candidate) {
      return actor.tenantId().equals(candidate.tenantId())
          && actor.ownerId().equals(candidate.ownerId());
    }

    private void requireOwner(Actor actor, String tenantId, String ownerId) {
      if (!actor.tenantId().equals(tenantId) || !actor.ownerId().equals(ownerId)) {
        throw new FileBridgeException(FileBridgeErrorCode.ACCESS_DENIED, "Resource not found");
      }
    }
  };
}
```

对无权访问的具体文件和上传任务，建议返回与资源不存在相同的外部表现，避免泄露资源是否存在。

## 7. 最小完整配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/file_bridge?serverTimezone=UTC
    username: filebridge
    password: ${FILE_BRIDGE_DB_PASSWORD}

file-bridge:
  enabled: true
  default-storage: local-main
  web:
    enabled: true
    base-path: /api/file-bridge
  upload:
    max-file-size: 2GB
    preferred-part-size: 8MB
    task-ttl: 24h
    lease-duration: 10m
  deduplication:
    enabled: true
    scope: USER
  cleanup:
    enabled: true
    interval: 30m
    unreferenced-retention: 24h
  storages:
    local-main:
      type: local
      root-path: ./data/files
      temp-path: ./data/uploads
```

## 8. 启动检查清单

- [ ] Java 和 Spring Boot 版本符合要求
- [ ] MySQL 脚本已经按顺序执行
- [ ] `default-storage` 对应实例真实存在
- [ ] 本地目录可创建、可读、可写，或云存储凭证有效
- [ ] 已提供 `CurrentActorProvider`
- [ ] 已提供 `FileAccessPolicy`
- [ ] 需要 REST 时已参考 `example` 在业务工程中实现 Controller
- [ ] 使用云存储时已引入对应 `storage-*` 模块
- [ ] 密钥来自环境变量或宿主密钥系统，而不是提交到仓库

## 9. 暂不使用时关闭

如果某个环境只希望保留依赖但暂不启用 FileBridge：

```yaml
file-bridge:
  enabled: false
```

关闭后不会创建 FileBridge 自动配置 Bean，也不要求提供存储实例和安全扩展 Bean。
