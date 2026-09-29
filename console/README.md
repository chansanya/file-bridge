# FileBridge 控制台与前端集成指南

本模块（`console/`）是 FileBridge 的独立管理控制台与前端组件中心，基于 **Vue 3 + TypeScript + Vite + Naive UI + Lucide 图标** 构建。

不仅可作为一个独立的前端后台管理系统运行，其内部的纯逻辑组合式函数（`useFileBridge`）可 **100% 解耦移植** 到任何现有的外部 Vue 3 业务项目中。

---

## 1. 独立运行与开发调试

### 1.1 安装依赖与启动本地开发
```bash
cd console
npm install
npm run dev
```
开发服务器默认运行在 `http://localhost:5173/`。

`vite.config.ts` 已内置本地反向代理：
```typescript
server: {
  port: 5173,
  proxy: {
    '/api/file-bridge': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```
无需担心浏览器跨域问题。

### 1.2 一键注入 Spring Boot 单体 Fat JAR
`vite.config.ts` 中已将打包输出目录配置为后端的静态资源目录：
```typescript
build: {
  outDir: '../example/src/main/resources/static',
  emptyOutDir: true,
}
```
只要在 `console` 目录下执行：
```bash
npm run build
```
编译产物将自动输出到 `example/src/main/resources/static/`，随后执行 `./mvnw clean package -DskipTests -pl example -am` 即可打出包含完整蓝白控制台的单体可执行 JAR 包。

---

## 2. 将核心上传/下载能力快速移植到其他 Vue 3 项目

如果你已有自己的业务后台（如基于 Element Plus、Ant Design Vue、Naive UI 或纯 Tailwind 构建），**无需引入任何重型组件包袱**，只需要拷贝核心逻辑文件。

### 2.1 拷贝核心文件
将以下三个文件拷贝至你目标项目的 `src/composables/`（或工具函数目录）：
```text
your-project/src/composables/
├── useFileBridge.ts   # 核心组合式函数，管理全部状态与生命周期
├── sha256.ts          # 纯 TypeScript 增量流式 SHA-256 算法引擎
└── hash-worker.ts     # 独立后台 Web Worker 计算脚本
```

### 2.2 在业务页面中直接调用

#### 基础初始化
```typescript
import { useFileBridge } from '@/composables/useFileBridge';

// 支持自定义后端 API 前缀及全局提示回调
const {
  fileQueue,          // 响应式上传任务队列
  activeDownloads,    // 响应式下载任务队列
  addFiles,           // 添加文件触发双模上传
  pauseUpload,        // 暂停上传
  resumeUpload,       // 恢复上传
  retryUpload,        // 断点续传重试
  cancelUpload,       // 取消上传并释放资源
  downloadFile,       // 触发下载（支持普通流式与并发分片）
  fetchFileList,      // 分页检索文件列表
  deleteFile,         // 删除文件引用
  createAccessUrl,    // 生成临时访问直链
} = useFileBridge({
  apiBase: '/api/file-bridge',
  onUploadError(item, error) {
    console.warn(`文件 ${item.name} 上传异常:`, error);
  },
  onUploadSuccess(item) {
    console.info(`文件 ${item.name} 上传成功，fileId=${item.fileId}`);
  },
});
```

#### 文件上传调用（自动分流 + 断点续传）
```vue
<template>
  <input type="file" multiple @change="(e) => addFiles(e.target.files)" />

  <div v-for="item in fileQueue" :key="item.id">
    <span>{{ item.name }} - {{ item.progress }}% ({{ item.speed }})</span>
    <button v-if="item.status === 'UPLOADING'" @click="pauseUpload(item)">暂停</button>
    <button v-if="item.status === 'PAUSED'" @click="resumeUpload(item)">继续</button>
    <button v-if="item.status === 'ERROR'" @click="retryUpload(item)">断点重试</button>
  </div>
</template>
```

- **≤ 10MB 文件**：自动走流式直传通道，零哈希计算开销；
- **> 10MB 文件**：自动点火 Web Worker 独立线程计算特征值，并发分片上传（带 3 次网络故障自动重试），断网后重新添加同一文件自动从断点续传。

#### 文件下载调用（三模对齐）
```typescript
// 模式 1：浏览器原生下载（零 JS 内存消耗，数据直写系统硬盘，适合 5GB+ 超大文件）
function downloadNative(fileId: string, fileName: string) {
  const a = document.createElement('a');
  a.href = `/api/file-bridge/files/${fileId}/download`;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

// 模式 2：应用内普通流式下载（带页面实时进度条与 MB/s 测速）
await downloadFile(fileId, fileName, fileSize, {
  chunked: false, // 走标准 200 OK 全量流
});

// 模式 3：多线程并发分片下载（HTTP Range 206 协议，带切片雷达联动）
await downloadFile(fileId, fileName, fileSize, {
  chunked: true,      // 启用 Range 切片并发
  concurrency: 4,     // 并发数（默认 4）
  partSize: 8 * 1024 * 1024, // 单片大小 8MB
});
```

#### 资产列表查询与分页
```typescript
// 分页查询当前用户资产
const res = await fetchFileList(1, 10, '关键词');
console.log('总记录数:', res.total);
console.log('数据列表:', res.items);
```

---

## 3. 将整套控制台组件完整复用

如果你希望直接把当前项目实现的蓝白企业级控制台集成进已有的后台系统中：

1. 确保目标工程安装了 Naive UI 与 Lucide 图标库：
   ```bash
   npm install naive-ui lucide-vue-next
   ```
2. 拷贝 `console/src/components/ConsoleWorkbench.vue` 及其引用的 composable；
3. 在你的业务路由或页面中直接挂载：
   ```vue
   <script setup>
   import { NConfigProvider, NMessageProvider, NDialogProvider } from 'naive-ui';
   import ConsoleWorkbench from '@/components/ConsoleWorkbench.vue';
   </script>

   <template>
     <NConfigProvider>
       <NMessageProvider>
         <NDialogProvider>
           <ConsoleWorkbench />
         </NDialogProvider>
       </NMessageProvider>
     </NConfigProvider>
   </template>
   ```

---

## 4. 业务异常友好中文化

`useFileBridge.ts` 导出了 `translateError(error)` 翻译引擎，可自动将后端错误码转换为友好的中文解释：

| 错误码 / 场景 | 中文提示 | 引导说明 |
| --- | --- | --- |
| `FILE_TOO_LARGE` | 文件超出大小配额 | 该文件体积超出了服务端配置的单文件上传上限（检查 max-file-size） |
| `FILE_NOT_FOUND` | 文件不存在 | 所请求的文件不存在或已被业务方删除 |
| `UPLOAD_EXPIRED` | 上传任务已过期 | 当前分片上传任务已超过系统有效时长，请重新上传 |
| `CHECKSUM_MISMATCH` | SHA-256 摘要不匹配 | 文件数据在传输过程中可能被损坏，服务端最终校验未通过 |
| `CAPABILITY_NOT_SUPPORTED` | 存储能力受限 | 当前存储为本地磁盘，不支持生成临时签名直链 |
| `NETWORK_ERROR` | 短暂网络抖动 | 系统已自动执行 3 次指数退避重试，无法恢复时保留断点 |
