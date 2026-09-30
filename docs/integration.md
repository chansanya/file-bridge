# FileBridge 完整集成与移植指南

本指南旨在指导如何将 FileBridge（包括后端存储内核与前端管理控制台）快速、规范地移植到外部业务工程中。

---

## 1. 基础依赖与环境要求

| 技术栈 | 版本要求 | 说明 |
| --- | --- | --- |
| **Java** | 8 或更高版本 | 领域模型已使用 Java 8 POJO 实现 |
| **Spring Boot** | 2.7.18 | 使用 `javax.servlet` 与 Spring Framework 5.3 |
| **数据库** | MySQL 5.7 ~ 8.4 LTS | 标准 SQL 语法兼容 |
| **前端** | Vue 3 + TypeScript | 支持任意构建工具（Vite / Webpack） |

---

## 2. 后端集成步骤（Spring Boot）

### 2.1 引入 Starter 依赖
在你的业务工程 `pom.xml` 中引入 FileBridge 官方 Starter：

```xml
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-spring-boot-starter</artifactId>
  <version>0.1.0-jdk8-SNAPSHOT</version>
</dependency>
```

若使用云对象存储，按需引入对应厂商适配器：
```xml
<!-- MinIO -->
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-minio</artifactId>
  <version>0.1.0-jdk8-SNAPSHOT</version>
</dependency>
<!-- 阿里云 OSS -->
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-aliyun</artifactId>
  <version>0.1.0-jdk8-SNAPSHOT</version>
</dependency>
<!-- 腾讯云 COS -->
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-storage-tencent</artifactId>
  <version>0.1.0-jdk8-SNAPSHOT</version>
</dependency>
```

### 2.2 执行数据库迁移脚本
按版本顺序执行 `docs/database/` 目录下的 3 个 SQL 脚本：
1. `V1__file_objects_and_references.sql`：物理对象表、业务引用表、幂等记录表；
2. `V2__multipart_upload.sql`：分片上传任务表、分片明细表；
3. `V3__cleanup_and_reconciliation.sql`：后台对账与物理对象回收表。

语法全量兼容 MySQL 5.7 及 MySQL 8.x。

### 2.3 核心配置属性 (`application.yml`)
```yaml
file-bridge:
  enabled: true
  default-storage: local-main
  upload:
    max-file-size: 20GB       # 单文件上传上限
    preferred-part-size: 8MB   # 推荐分片大小
    task-ttl: 24h              # 分片任务有效期
  deduplication:
    enabled: true             # 启用秒传
    scope: USER               # 秒传隔离范围：USER（同用户）或 TENANT（同租户）
  storages:
    local-main:
      type: local
      # 生产环境建议配置绝对路径，避免由于启动工作目录差异导致相对路径漂移
      root-path: ${FILE_BRIDGE_ROOT_PATH:/var/data/file-bridge/files}
      temp-path: ${FILE_BRIDGE_TEMP_PATH:/var/data/file-bridge/uploads}
```

### 2.4 提供可信身份与安全策略 Bean（必需项）
FileBridge 坚决不信任来自前端请求参数中的租户或用户 ID，必须由宿主应用从自身安全上下文中提取：

```java
@Configuration
public class FileBridgeSecurityConfig {

  @Bean
  public CurrentActorProvider currentActorProvider() {
    return () -> {
      // 从 Spring Security / SecurityContextHolder / JWT 中读取已校验的身份
      String tenantId = SecurityUtils.getCurrentTenantId();
      String userId = SecurityUtils.getCurrentUserId();
      return new Actor(tenantId, userId, Map.of());
    };
  }

  @Bean
  public FileAccessPolicy fileAccessPolicy() {
    return new FileAccessPolicy() {
      @Override
      public void checkUpload(Actor actor, String businessType, String businessId) {
        // 可选：校验业务上下文上传配额或权限
      }

      @Override
      public void checkRead(Actor actor, FileReference ref) {
        if (!actor.tenantId().equals(ref.tenantId()) || !actor.ownerId().equals(ref.ownerId())) {
          throw new FileBridgeException(FileBridgeErrorCode.ACCESS_DENIED, "Resource not found");
        }
      }

      @Override
      public void checkDelete(Actor actor, FileReference ref) {
        if (!actor.tenantId().equals(ref.tenantId()) || !actor.ownerId().equals(ref.ownerId())) {
          throw new FileBridgeException(FileBridgeErrorCode.ACCESS_DENIED, "Resource not found");
        }
      }

      @Override
      public void checkUploadTask(Actor actor, UploadTask task) {
        if (!actor.tenantId().equals(task.tenantId()) || !actor.ownerId().equals(task.ownerId())) {
          throw new FileBridgeException(FileBridgeErrorCode.ACCESS_DENIED, "Resource not found");
        }
      }

      @Override
      public boolean canReuse(Actor actor, FileReference candidate) {
        return actor.tenantId().equals(candidate.tenantId())
            && actor.ownerId().equals(candidate.ownerId());
      }
    };
  }
}
```

### 2.5 暴露 REST API 控制器
Starter 本身保持纯净的 Java 服务层抽象，不强绑特定的 Web 拦截规则。业务工程直接复用或拷贝 `example` 模块中的标准控制器：

- **`FileController.java`**：
  - `POST /api/file-bridge/files`：普通流式文件上传
  - `GET /api/file-bridge/files`：文件资产分页与模糊检索（返回 `PageResult<FileMetadata>`）
  - `GET /api/file-bridge/files/{id}/download`：全量普通下载（200 OK）与标准 HTTP Range 分片并发下载（206 Partial Content）一体机
  - `POST /api/file-bridge/files/{id}/access-url`：云对象存储 300 秒临时直链生成
  - `DELETE /api/file-bridge/files/{id}`：逻辑删除文件引用
- **`UploadController.java`**：
  - `POST /api/file-bridge/uploads`：分片任务初始化与秒传探针
  - `PUT /api/file-bridge/uploads/{id}/parts/{partNumber}`：单个二进制分片流式上传
  - `GET /api/file-bridge/uploads/{id}`：上传状态与已确认分片列表查询
  - `POST /api/file-bridge/uploads/{id}/complete`：触发后台合并校验（202 异步对账 / 200 成功）
  - `DELETE /api/file-bridge/uploads/{id}`：取消上传任务

---

## 3. 前端集成步骤（Vue 3）

前端提供两种层级的复用方式，各取所需。

### 方式 A：纯逻辑 Headless Composable 移植（推荐自由定制 UI）

#### 1. 复制核心逻辑文件
将项目根目录 `console/src/composables/` 下的 3 个文件复制到目标工程的 `src/composables/`：
- `useFileBridge.ts`：核心逻辑、全量 TypeScript 接口定义、业务错误字典 `translateError`；
- `sha256.ts`：纯 JS/TS 实现的零依赖增量哈希算法；
- `hash-worker.ts`：独立后台 Web Worker 哈希运算脚本。

#### 2. 在业务页面中使用
```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useFileBridge } from '@/composables/useFileBridge';

const {
  fileQueue,          // 响应式上传任务队列
  addFiles,           // 触发双模上传（≤10MB 直传，>10MB Web Worker 切片）
  pauseUpload,        // 暂停上传
  resumeUpload,       // 恢复上传
  retryUpload,        // 断点续传重试
  cancelUpload,       // 取消上传
  downloadFile,       // 触发下载（支持普通流式与并发分片）
  fetchFileList,      // 分页查询资产列表
  deleteFile,         // 物理/逻辑删除
} = useFileBridge({
  apiBase: '/api/file-bridge',
  onUploadError(item, err) {
    console.warn('上传遇到限制:', item.name, err);
  },
  onUploadSuccess(item) {
    console.info('文件入库成功:', item.name);
  },
});

// 分页列表查询
const fileList = ref([]);
const totalCount = ref(0);

const loadData = async () => {
  const res = await fetchFileList(1, 10, '');
  fileList.value = res.items;
  totalCount.value = res.total;
};

// 三模下载调用
// 1. 浏览器原生下载（零网页内存占用，直写磁盘）
const downloadNative = (fileId: string, name: string) => {
  const a = document.createElement('a');
  a.href = `/api/file-bridge/files/${fileId}/download`;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
};

// 2. 普通流式下载（带实时进度条与测速）
const downloadStream = async (row: any) => {
  await downloadFile(row.fileId, row.originalName, row.size, { chunked: false });
};

// 3. 多线程并发分片下载（HTTP Range 206）
const downloadChunked = async (row: any) => {
  await downloadFile(row.fileId, row.originalName, row.size, { chunked: true, concurrency: 4 });
};

onMounted(loadData);
</script>
```

---

### 方式 B：完整独立管理控制台复用

如果你希望直接使用蓝白现代化管理控制台：

1. **直接运行独立工程**：
   在 `console/` 目录下执行：
   ```bash
   npm install
   npm run dev
   ```
   Vite 会自动代理请求至本地 Spring Boot（`http://localhost:8080`），开发体验极速。

2. **单体 Fat JAR 一键交付**：
   在 `console/` 目录下执行：
   ```bash
   npm run build
   ```
   产物会自动打包并注入到后端的 `example/src/main/resources/static/` 目录下。接着执行：
   ```bash
   ./mvnw clean package -DskipTests -pl example -am
   java -jar example/target/example-0.1.0-jdk8-SNAPSHOT.jar
   ```
   启动后直接访问 `http://localhost:8080/`，单体 JAR 包自带完整的蓝白控制台，零外部 Web 服务器依赖！
