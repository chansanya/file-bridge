# FileBridge 配置参考

## 1. 配置总览

配置前缀为 `file-bridge`。

```yaml
file-bridge:
  enabled: true
  default-storage: local-main
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

## 2. 核心配置

| 配置项 | 默认值 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file-bridge.enabled` | `true` | 否 | 是否启用 FileBridge 自动配置 |
| `file-bridge.default-storage` | `local-main` | 启用时是 | 新上传文件默认使用的存储实例 ID |
| `file-bridge.storages` | 空 | 启用时是 | 存储实例映射，Key 是稳定实例 ID |

### `default-storage` 与实例 ID

```yaml
file-bridge:
  default-storage: minio-main
  storages:
    minio-main:
      type: minio
      # ...
```

`default-storage` 必须等于 `storages` 中某个已启用实例的 Key。

实例 ID 与平台类型是两个概念：

```yaml
storages:
  minio-primary:
    type: minio
  minio-archive:
    type: minio
```

这样可以配置多个同类型实例。实例 ID 会写入对象元数据，切换默认存储不会改变历史对象的读取位置。

## 3. 独立示例工程配置

Starter 不提供 Web Controller，因此没有 Starter 级别的 Web 开关。

独立 `example` 工程自行定义以下示例属性：

```yaml
example:
  file-bridge:
    base-path: /api/file-bridge
```

该属性只属于演示工程，用于设置示例 Controller 的路径前缀，不是 FileBridge Starter 的公共配置项。真实业务项目可以采用自己的路径、响应结构和鉴权方式。

示例前端还在 `useFileBridge.js` 中提供浏览器侧默认参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `apiBase` | `/api/file-bridge` | REST API 前缀 |
| `chunkThreshold` | `10 MiB` | 小文件直传与大文件分片的分界 |
| `preferredPartSize` | `8 MiB` | 浏览器初始分片估算值，最终以服务端响应为准 |
| `hashBlockSize` | `4 MiB` | 增量 SHA-256 读取块大小 |
| `concurrency` | `3` | 单文件并发上传分片数 |

这些参数只控制 Example 浏览器行为，不属于 `file-bridge.*` 服务端配置，也不能绕过服务端限制。详见 [Example 上传控制台](example.md)。

## 4. 上传配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `file-bridge.upload.max-file-size` | `2GB` | 单文件大小上限 |
| `file-bridge.upload.preferred-part-size` | `8MB` | 首选分片大小，最终值还会受存储平台限制 |
| `file-bridge.upload.task-ttl` | `24h` | 未完成上传任务有效期 |
| `file-bridge.upload.lease-duration` | `10m` | 后台完成任务的处理租约时长 |

`preferred-part-size` 不是强制值。实际分片大小取配置偏好与存储平台最小值、最大值及最大分片数的交集。

## 5. 秒传配置

| 配置项 | 默认值 | 可选值 | 说明 |
| --- | --- | --- | --- |
| `file-bridge.deduplication.enabled` | `true` | `true` / `false` | 是否启用秒传候选查询 |
| `file-bridge.deduplication.scope` | `USER` | `DISABLED`、`USER`、`TENANT` | 摘要候选查询范围 |

范围说明：

- `DISABLED`：关闭秒传；
- `USER`：只复用当前租户、当前用户已有的有效引用；
- `TENANT`：允许在当前租户内寻找候选，但最终仍由 `FileAccessPolicy.canReuse` 决定是否有权复用。

客户端摘要只是候选条件。只有服务端已完成最终校验且状态为 `AVAILABLE` 的对象才会参与秒传。

## 6. 清理配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `file-bridge.cleanup.enabled` | `true` | 是否启用清理配置标志 |
| `file-bridge.cleanup.interval` | `30m` | 清理周期配置值 |
| `file-bridge.cleanup.unreferenced-retention` | `24h` | 无引用对象删除前的保留时间 |

当前调度器读取的周期属性为：

```yaml
file-bridge:
  worker:
    interval: 5s
  cleanup:
    interval: 30m
  reconciliation:
    interval: 15m
```

> 清理和对账属于破坏性或修复性后台任务。上线前应结合数据量、对象存储限流和数据库压力调整周期。

## 7. 本地存储

本地存储不需要额外依赖：

```yaml
file-bridge:
  default-storage: local-main
  storages:
    local-main:
      type: local
      enabled: true
      root-path: /srv/file-bridge/files
      temp-path: /srv/file-bridge/uploads
```

| 配置项 | 必填 | 说明 |
| --- | --- | --- |
| `type` | 是 | 固定为 `local` |
| `enabled` | 否 | 默认 `true` |
| `root-path` | 是 | 最终对象目录 |
| `temp-path` | 是 | 临时对象和分片目录，不能与 `root-path` 相同 |

部署限制：

- 单实例部署可以使用本机目录；
- 多实例部署必须使用所有实例都能访问的共享文件系统；
- 只共享数据库但不共享文件目录，无法完成跨实例续传和下载；
- 目录必须可创建、可读、可写；
- 不要把临时目录暴露为静态资源目录。

## 8. MinIO

先添加依赖：

```xml
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-minio</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

配置：

```yaml
file-bridge:
  default-storage: minio-main
  storages:
    minio-main:
      type: minio
      endpoint: http://localhost:9000
      bucket: file-bridge
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
```

必填项：`endpoint`、`bucket`、`access-key`、`secret-key`。

## 9. 阿里云 OSS

先添加依赖：

```xml
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-aliyun</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

配置：

```yaml
file-bridge:
  default-storage: aliyun-main
  storages:
    aliyun-main:
      type: aliyun-oss
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      bucket: your-bucket
      access-key: ${ALIYUN_ACCESS_KEY_ID}
      secret-key: ${ALIYUN_ACCESS_KEY_SECRET}
```

必填项：`endpoint`、`bucket`、`access-key`、`secret-key`。

## 10. 腾讯云 COS

先添加依赖：

```xml
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-tencent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

配置：

```yaml
file-bridge:
  default-storage: tencent-main
  storages:
    tencent-main:
      type: tencent-cos
      region: ap-guangzhou
      bucket: your-bucket-1250000000
      access-key: ${TENCENT_SECRET_ID}
      secret-key: ${TENCENT_SECRET_KEY}
```

必填项：`region`、`bucket`、`access-key`、`secret-key`。

## 11. 多存储实例

```yaml
file-bridge:
  default-storage: local-main
  storages:
    local-main:
      type: local
      root-path: /srv/file-bridge/files
      temp-path: /srv/file-bridge/uploads
    minio-archive:
      type: minio
      endpoint: https://minio.example.com
      bucket: archive
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
```

当前新上传使用 `default-storage`。历史对象始终按数据库中记录的 `storageId` 查找原存储实例，因此：

- 可以切换新上传的默认存储；
- 不得在仍有历史对象时直接删除旧实例配置；
- 不得复用旧实例 ID 指向另一套不相关存储。

## 12. 密钥安全

不要把真实凭证直接写入仓库：

```yaml
access-key: ${STORAGE_ACCESS_KEY}
secret-key: ${STORAGE_SECRET_KEY}
```

当前云适配器要求显式提供访问密钥。生产环境应通过环境变量或宿主密钥管理系统注入，并限制配置文件、日志和诊断接口的访问权限。

## 13. 启动失败排查

| 错误场景 | 常见原因 | 处理方式 |
| --- | --- | --- |
| `Unknown storage id` | `default-storage` 与实例 Key 不一致 | 修正 ID 或注册自定义 Provider |
| `Missing storage type` | 存储实例未配置 `type` | 设置 `local`、`minio`、`aliyun-oss` 或 `tencent-cos` |
| `Storage module is not on the classpath` | 配置了云类型但没引入模块 | 添加对应 `file-bridge-storage-*` 依赖 |
| 缺少 `CurrentActorProvider` | 启用了 JDBC 业务服务但未提供可信身份 | 注册身份 Bean |
| 缺少 `FileAccessPolicy` | 未提供授权策略 | 注册访问策略 Bean |
| 本地目录初始化失败 | 路径无权限或非法 | 检查目录权限和挂载配置 |
## 14. 上传与下载日志

FileBridge 默认只在关键生命周期节点输出日志，不按缓冲区读取次数刷屏：

| 级别 | 记录内容 |
| --- | --- |
| `INFO` | 普通上传开始/完成、分片任务创建、秒传命中、完成请求、后台合并、最终校验、完整下载完成、取消任务 |
| `DEBUG` | 单个分片完成、下载流打开/非完整关闭、Example HTTP 请求摘要和 Range 信息 |
| `WARN` | 可识别的业务失败、存储失败、待重试或需要对账的操作 |
| `ERROR` | 未知运行时异常，并保留异常堆栈 |

日志使用 `fileId`、`uploadId`、`storageId`、字节数、分片序号、耗时和错误码定位问题。不会记录文件正文、完整摘要、对象路径、Bucket、访问密钥、幂等键或签名 URL。

宿主应把请求追踪 ID 放入日志上下文。Example 已通过 `RequestIdFilter` 写入 MDC，并配置：

```yaml
logging:
  pattern:
    level: "%5p [requestId:%X{requestId:-}]"
  level:
    io.github.chansan.filebridge.upload: info
    io.github.chansan.example.web.ApiLoggingFilter: debug
```

生产环境建议保持业务生命周期日志为 `INFO`，仅在排障期间临时开启 `io.github.chansan.filebridge.upload: debug`。`ApiLoggingFilter` 属于 Example 参考实现，不会由 Starter 自动注册。

Example 页面还提供仅保存在当前浏览器页面内的运行日志抽屉，用于查看客户端上传、下载和 HTTP 请求过程。该抽屉不读取服务端日志，也不属于 Starter 能力，详见 [Example 管理控制台](example.md)。

## 15. 指标端口

FileBridge 通过 `FileBridgeMetrics` 端口记录以下语义：

| 指标 | 说明 |
| --- | --- |
| `filebridge.operations` | 按 operation/outcome 统计操作次数 |
| `filebridge.amount` | 上传、下载字节数或后台处理数量 |
| `filebridge.duration` | 上传、下载和完成任务耗时 |

当前 operation 包括 `upload`、`download`、`completion`、`cleanup.uploads`、`cleanup.objects` 和 `reconciliation`。下载结果区分 `success`、`incomplete` 和 `failure`。

Starter 当前提供空实现以避免强制引入 Micrometer。宿主需要真实指标时，应注册自定义 `FileBridgeMetrics` Bean；Starter 不会因为 classpath 中存在 `MeterRegistry` 就自动绑定 Micrometer。
