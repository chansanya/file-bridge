# Example 管理控制台

## 1. 定位

`example` 是独立 Spring Boot 接入工程，只通过 `file-bridge-spring-boot-starter` 使用 FileBridge。`console` 是对应的 Vue 3 演示前端，构建产物输出到 `example/src/main/resources/static`。

控制台演示以下能力：

- 文件资产分页查询、搜索、下载、删除和临时访问地址；
- 小文件普通上传；
- 大文件摘要计算、分片上传、秒传和断点续传；
- 上传与下载进度、速率和分片状态；
- 当前页面内的客户端运行日志。

Example 只用于本地集成演示，不是随 Starter 发布的前端 SDK，也不能直接作为生产管理后台。

## 2. 前端结构

| 路径 | 作用 |
| --- | --- |
| `console/src/components/ConsoleWorkbench.vue` | 页面布局、文件表格、上传任务和日志抽屉 |
| `console/src/composables/useFileBridge.ts` | HTTP 调用、上传、下载、续传和状态调度 |
| `console/src/composables/useOperationLog.ts` | 当前页面运行日志、未读数量和日志导出 |
| `console/src/composables/sha256.ts` | 浏览器增量 SHA-256 实现 |
| `console/src/composables/hash-worker.ts` | 大文件摘要 Web Worker |
| `example/src/main/resources/static` | Vite 生成的 Example 静态资源 |

前端使用 Vue 3、TypeScript、Naive UI、Lucide 和 Vite。禁止直接编辑 `static/assets` 下的压缩产物，所有界面修改都应在 `console/src` 完成后重新构建。

## 3. 开发与构建

```bash
cd console
npm ci
npm run type-check
npm run dev
```

Vite 开发服务器默认监听 <http://localhost:5173/>，并把 `/api/file-bridge` 代理到 <http://localhost:8080/>。

生成 Example 静态资源：

```bash
cd console
npm run type-check
npm run build
```

随后启动 Example：

```bash
./mvnw install -DskipTests
java -jar example/target/example-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

## 4. 上传策略

前端默认参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| API 前缀 | `/api/file-bridge` | Example REST API 前缀 |
| 普通上传阈值 | `10 MiB` | 小于等于阈值走普通上传 |
| 推荐分片大小 | `8 MiB` | 服务端响应后以服务端值为准 |
| 摘要读取块 | `4 MiB` | Worker 增量读取大小 |
| 分片并发数 | `4` | 单文件上传或下载并发数 |

大文件流程：

```text
Web Worker 增量计算 SHA-256
  -> 查询本地 uploadId
  -> 恢复服务端确认的已完成分片
  -> 初始化任务或命中秒传
  -> 并发上传缺失分片
  -> 请求后台合并和校验
  -> 轮询直至获得 fileId
```

`localStorage` 只保存 `fb:{fileSize}:{sha256}` 对应的 `uploadId`，不保存文件内容。完成后删除该记录。

## 5. 运行日志抽屉

顶部导航的“运行日志”按钮打开右侧抽屉。日志只记录浏览器当前页面执行的关键过程：

- 文件加入队列和摘要计算；
- 普通上传、分片初始化、续传、秒传、暂停、恢复和取消；
- 分片上传或 Range 下载的 25%、50%、75%、100% 里程碑；
- 服务端合并、校验和最终结果；
- 文件列表、删除、下载和临时地址请求；
- HTTP 状态、耗时、业务错误码和 `requestId`。

抽屉不会自动打开。关闭期间产生的日志通过顶部角标提示，存在未读错误时角标显示为红色；打开抽屉后未读数量清零。

日志最多保留 300 条，最新记录在前，刷新页面后清空。支持按级别筛选、复制当前筛选结果和清空。日志不会保存到 `localStorage` 或 `sessionStorage`，也不会记录文件正文、完整 SHA-256、签名 URL、请求体或敏感请求头。

## 6. 已知边界

- 普通上传使用 `XMLHttpRequest` 获取上传进度，不支持真正的暂停续传；
- 分片暂停只停止领取新分片，不强制中止已经发出的请求；
- 浏览器分片下载会在内存中组装完整 Blob，不适合无限大的文件；
- 客户端摘要只用于秒传候选和续传绑定，最终摘要仍由服务端重新计算；
- 运行日志用于当前页面排障，不是审计日志，也不展示服务端 Logback 日志；
- 前端构建的大包体积警告属于当前单页演示工程的已知限制。
