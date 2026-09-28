# Example 上传控制台

## 1. 定位

`example` 是独立 Spring Boot 接入工程，只通过 `file-bridge-spring-boot-starter` 使用 FileBridge。它不是可复用前端 SDK，也不会随 Starter 一起发布。

示例控制台用于演示：

- 小文件普通上传；
- 大文件分片上传；
- SHA-256 秒传探测；
- 已上传分片查询和断点续传；
- 分片并发调度；
- 上传进度和速率展示；
- 暂停、恢复、取消、下载和删除。

访问地址：

```text
http://localhost:8080/
```

## 2. 前端结构

| 文件 | 作用 |
| --- | --- |
| `static/index.html` | 页面结构、主题样式、Vue 模板和 Import Map |
| `static/app.js` | Vue 应用入口、界面状态、拖放交互和图标组件 |
| `static/useFileBridge.js` | 上传队列、摘要计算、普通上传、分片上传和续传逻辑 |
| `static/sha256.js` | 浏览器增量 SHA-256 实现和主线程降级方案 |
| `static/vendor/vue.esm-browser.js` | 固定版本的 Vue 3.4.21 浏览器 ESM 文件 |

页面通过 Import Map 加载仓库内的 Vue 文件：

```html
<script type="importmap">
  {
    "imports": {
      "vue": "./vendor/vue.esm-browser.js"
    }
  }
</script>
```

不需要 Node.js、Vite 或打包步骤。JavaScript 使用原生 ESM，不使用 TypeScript。

## 3. 默认上传参数

`useFileBridge(options)` 支持覆盖默认参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `apiBase` | `/api/file-bridge` | Example REST API 前缀 |
| `chunkThreshold` | `10 MiB` | 小于等于该值走普通上传，大于该值走分片上传 |
| `preferredPartSize` | `8 MiB` | 初始化前用于估算分片数；服务端响应后以服务端值为准 |
| `hashBlockSize` | `4 MiB` | 浏览器增量计算 SHA-256 的读取块大小 |
| `concurrency` | `3` | 单个文件同时上传的最大分片数 |

当前 `app.js` 使用默认配置：

```javascript
const {
  fileQueue,
  addFiles,
  pauseItem,
  resumeItem,
  cancelItem,
} = useFileBridge();
```

如需覆盖：

```javascript
const bridge = useFileBridge({
  apiBase: '/api/file-bridge',
  chunkThreshold: 20 * 1024 * 1024,
  preferredPartSize: 8 * 1024 * 1024,
  hashBlockSize: 4 * 1024 * 1024,
  concurrency: 3,
});
```

前端参数不能突破服务端限制。最终文件大小、分片大小、分片数量和权限仍由服务端校验。

## 4. 小文件普通上传

大小不超过 `chunkThreshold` 的文件使用：

```http
POST /api/file-bridge/files
Content-Type: multipart/form-data
```

示例使用 `XMLHttpRequest`，以便读取原生上传进度事件和计算传输速率。

请求包含：

- 文件正文；
- 可选 `businessType`；
- 可选 `businessId`；
- 随机生成的 `Idempotency-Key`。

普通上传完成后，队列项进入 `SUCCESS`，并展示下载和删除操作。

## 5. 大文件分片上传

大于 `chunkThreshold` 的文件按以下流程执行：

```text
Web Worker 增量计算 SHA-256
  -> 查询 localStorage 中的 uploadId
  -> 查询服务端已完成分片
  -> 未命中时初始化上传任务或检查秒传
  -> 最多并发上传 3 个缺失分片
  -> 请求 complete
  -> 轮询 COMPLETING / VERIFYING
  -> 获得 fileId
```

服务端返回的 `partSize` 和 `totalParts` 会覆盖浏览器的初始估算值。

## 6. 摘要计算

大文件摘要优先在 Web Worker 中计算，避免阻塞页面交互。Worker 按 `hashBlockSize` 分块读取文件，并持续回报计算进度。

如果浏览器不支持 Worker 或 Worker 创建失败，则退回主线程增量计算。两种方式都不会一次性把整个文件读入内存。

浏览器摘要只用于：

- 秒传候选匹配；
- 绑定续传任务与本地文件。

最终文件摘要仍由服务端回读最终对象后重新计算，不能信任客户端结果。

## 7. 断点续传

浏览器使用以下 Key 保存任务 ID：

```text
fb:{fileSize}:{sha256}
```

值为服务端返回的 `uploadId`。

重新选择同一文件时：

1. 使用文件大小和 SHA-256 找到本地 `uploadId`；
2. 请求 `GET /uploads/{uploadId}`；
3. 读取服务端确认的 `completedParts`；
4. 只补传缺失分片；
5. 任务不存在或失效时删除本地记录并重新初始化。

成功完成后会删除对应的 `localStorage` 记录。

## 8. 队列和状态

文件队列按文件逐个处理；一个大文件内部的分片可以并发上传。

| 状态 | 界面含义 |
| --- | --- |
| `IDLE` | 等待队列调度 |
| `HASHING` | 正在计算完整 SHA-256 |
| `UPLOADING` | 正在普通上传或上传分片 |
| `PAUSED` | 暂停分配新的分片 |
| `COMPLETING` | 服务端正在合并分片 |
| `INSTANT_HIT` | 秒传命中并已获得 fileId |
| `SUCCESS` | 上传和服务端校验完成 |
| `ERROR` | 上传、合并或校验失败 |
| `CANCELLED` | 用户取消任务 |

控制台顶部显示当前活跃任务数量和上传速率，分片文件还会显示已完成分片矩阵。

## 9. 操作行为

### 暂停和恢复

分片上传暂停后不会再领取新分片；已经发出的 HTTP 请求可能继续完成。

普通小文件使用单次 `XMLHttpRequest`，当前示例不提供真正的断点暂停能力。需要暂停续传时应使用分片上传流程。

### 取消

取消操作会：

1. 标记前端任务终止；
2. 停止摘要 Worker；
3. 中止普通上传 XHR；
4. 已创建分片任务时调用服务端取消接口；
5. 从当前页面队列移除任务。

### 下载和删除

上传成功并获得 `fileId` 后：

- 下载按钮打开 `GET /files/{fileId}/download`；
- 删除按钮调用 `DELETE /files/{fileId}`，然后移除队列项。

## 10. 已知边界

- Example 是集成演示，不是生产级文件管理后台；
- 文件队列当前按文件串行处理；
- 暂停只阻止新的分片调度，不保证中止已发送请求；
- `localStorage` 只保存续传任务 ID，不保存文件内容；
- 清理浏览器站点数据会丢失本地续传索引，但服务端任务仍会等待过期清理；
- 生产前端应针对网络错误设置明确的重试上限和退避策略；
- 浏览器会自行生成 `Content-Length`，Fetch 请求不应由脚本手工设置该受限请求头；
- 对 `409 Conflict` 应区分幂等重试与不同内容冲突，不能无条件当作成功；
- Vue 文件为仓库内固定版本，升级时必须同步检查浏览器兼容性和许可证信息。

## 11. 前端格式检查

业务 HTML 和 JavaScript 使用 Prettier：

```bash
npx prettier@3.9.9 --write \
  example/src/main/resources/static/index.html \
  example/src/main/resources/static/*.js

npx prettier@3.9.9 --check \
  example/src/main/resources/static/index.html \
  example/src/main/resources/static/*.js
```

`vendor/vue.esm-browser.js` 是第三方压缩文件，不应使用项目格式化器重写。
