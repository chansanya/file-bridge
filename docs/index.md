# FileBridge 文档

FileBridge 是一个可嵌入 Spring Boot 的通用文件服务组件，提供普通上传、大文件分片续传、授权范围内秒传、HTTP Range 分片下载、本地及云对象存储适配。

## 核心能力

- **普通上传**：流式处理，支持大小限制和幂等控制。
- **分片上传**：Web Worker 计算摘要、并发分片、乱序上传、失败重试。
- **断点续传**：持久化上传任务，服务端记录已确认分片，只补传缺失部分。
- **秒传**：仅在宿主授权范围内复用已通过服务端最终校验的对象。
- **三模下载**：浏览器原生下载、页面内流式下载、HTTP Range 206 并发分片下载。
- **多存储适配**：本地文件系统、MinIO、阿里云 OSS、腾讯云 COS。
- **管理控制台**：独立 Vue 3 + TypeScript + Naive UI 控制台，可前后端分离开发，也可打包进单体 JAR。

## 快速导航

- 想把 FileBridge 接入你的 Spring Boot 工程？先看 [快速开始](integration.md)。
- 想了解全部 HTTP 接口和状态码？看 [REST API](api.md)。
- 想调整上传限制、分片大小、存储实例和清理策略？看 [配置参考](configuration.md)。
- 想理解模块边界、状态机和故障恢复设计？看 [架构说明](architecture.md)。
- 想本地运行演示控制台？看 [控制台](example.md)。
- 想把上传下载 Composable 移植到自己的 Vue 3 项目？看 [前端移植](console.md)。
- 想初始化数据库？看 [迁移脚本](database/README.md)。

## 本地演示

```bash
docker compose up -d mysql
./mvnw install -DskipTests
./mvnw -f example/pom.xml spring-boot:run
```

启动后访问 <http://localhost:8080/>。

> Demo 身份与权限策略仅用于本地演示，禁止直接用于生产环境。

## 文档站点

本文档由 MkDocs Material 构建，并通过 GitHub Actions 自动发布到 GitHub Pages。修改 `docs/` 或 `mkdocs.yml` 后推送到 `main` 分支即可自动更新。
