# FileBridge

FileBridge 是一个可嵌入 Spring Boot 的通用文件服务组件，提供普通上传、分片续传、授权范围内秒传、本地及云对象存储适配。

## 当前能力

- 普通文件流式上传、查询、下载和删除
- 分片上传、断点续传、分片重试和后台合并
- 基于 SHA-256 的服务端最终校验
- 用户或租户授权范围内的秒传
- 本地文件系统、MinIO、阿里云 OSS、腾讯云 COS
- JDBC 持久化和 MySQL 8.4 数据库脚本
- Spring Boot 自动配置和 Java 服务接口
- 独立 `example` 工程演示真实 Starter 接入和 REST 封装

## 技术基线

| 项目 | 版本或要求 |
| --- | --- |
| Java | 17 |
| Spring Boot | 4.1.1 |
| Maven | 3.9.16，仓库已提供 Wrapper |
| MySQL | 8.4 LTS |
| 项目版本 | `0.1.0-SNAPSHOT` |

## 接入前必须理解

启用 FileBridge 后，必须提供至少一个可用的 `StorageProvider`。

这里的“存储实例”不是说必须额外部署 MinIO 或购买云存储。本地目录同样是一个存储实例：

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

之所以必须提供存储实例，是因为：

1. MySQL 只保存文件元数据、业务引用和上传任务，不保存文件正文。
2. 上传、下载、分片合并和物理删除都必须由 `StorageProvider` 执行。
3. 每个物理对象都会记录自己的 `storageId`，历史文件需要通过原存储实例读取。
4. 启动时检查默认存储可以尽早暴露错误，避免上传过程中才发现文件无处保存。

如果应用暂时不使用 FileBridge，可直接关闭：

```yaml
file-bridge:
  enabled: false
```

## 快速导航

- [完整接入步骤](docs/integration.md)
- [配置项和存储示例](docs/configuration.md)
- [REST API](docs/api.md)
- [架构与模块边界](docs/architecture.md)
- [数据库脚本说明](docs/database/README.md)

## 本地验证

```bash
./mvnw test
```

启动示例依赖 MySQL：

```bash
docker compose up -d mysql
./mvnw install -DskipTests -pl file-bridge-spring-boot-starter -am
./mvnw -f example/pom.xml spring-boot:run
```

示例页面：<http://localhost:8080/>

> `example` 使用独立 Spring Boot Parent，只通过 `file-bridge-spring-boot-starter` 接入组件，不依赖 FileBridge 内部模块。
>
> `demo` Profile 中的固定身份和权限策略只用于本地演示，禁止直接用于生产环境。

## 代码格式

Java 代码统一使用 Google Java Format 1.25.2，并通过 Spotless 固化：

```bash
./mvnw spotless:apply
./mvnw spotless:check
```

POM 使用 SortPom 4.0.0，构建时会自动检查：

```bash
./mvnw validate
```

`example` 中的 HTML、CSS 和原生 JavaScript 使用 Prettier 3.9.9：

```bash
npx prettier@3.9.9 --write \
  example/src/main/resources/static/index.html \
  example/src/main/resources/static/*.js

npx prettier@3.9.9 --check \
  example/src/main/resources/static/index.html \
  example/src/main/resources/static/*.js
```

## License

MIT
