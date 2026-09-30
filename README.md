# FileBridge

> **JDK 8 兼容分支**：当前分支面向遗留 Java 8 / Spring Boot 2.7.18 项目。现代 Java 17 / Spring Boot 4 实现请使用 `main` 分支。

FileBridge 是一个可嵌入 Spring Boot 的通用文件服务组件，提供普通上传、分片续传、授权范围内秒传、本地及云对象存储适配。

## 当前能力

- 普通文件流式上传、分页列表检索、HTTP Range 206 分片与原生三模下载
- 大文件切片并发上传、网络故障自动重试、断点续传与后台异步合并对账
- 基于 Web Worker 独立线程计算 SHA-256 特征值，主渲染线程零卡顿
- 用户或租户授权范围内的毫秒级秒传探针
- 本地文件系统、MinIO、阿里云 OSS、腾讯云 COS 存储适配
- JDBC 持久化（语法全量兼容 MySQL 5.7 到 8.4 LTS）
- Spring Boot 自动配置和纯净 Java 服务接口
- 独立 `example` 工程演示真实 Starter 接入和标准 REST 封装
- 独立 Vue 3 + TypeScript + Naive UI 管理控制台（`console/`），支持前后端分离独立开发与一键注入 Fat JAR 单体交付

## 技术基线

| 项目 | 版本或要求 |
| --- | --- |
| Java | 8 或更高版本 |
| Spring Boot | 2.7.18 |
| Maven | 3.6.3 或更高版本，仓库已提供 Wrapper |
| MySQL | 8.4 LTS |
| 项目版本 | `0.1.0-jdk8-SNAPSHOT`（JDK 8 兼容分支） |

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

- [在线文档](https://chansanya.github.io/file-bridge/)
- [完整接入步骤](docs/integration.md)
- [配置项和存储示例](docs/configuration.md)
- [REST API](docs/api.md)
- [Example 上传控制台](docs/example.md)
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
./mvnw -f example/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
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

Example 前端源码位于 `console`，使用 Vite 构建到 `example/src/main/resources/static`：

```bash
cd console
npm ci
npm run type-check
npm run build
```

不要直接修改 `static/assets` 下的构建产物。

## License

MIT
