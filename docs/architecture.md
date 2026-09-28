# FileBridge 架构说明

## 1. 设计目标

FileBridge 采用端口与适配器结构，将文件业务流程、数据库持久化、HTTP 接口和具体存储 SDK 分离。

核心原则：

- 业务服务不依赖 Servlet 请求和具体云 SDK；
- 文件正文流式处理，不把大文件完整读入内存；
- 物理对象与业务文件引用分离；
- 身份来自宿主可信上下文；
- 只有服务端完成最终校验的对象才能下载或参与秒传；
- 并发控制依赖数据库约束、条件更新和租约，不依赖进程内锁。

## 2. 模块职责

| 模块 | 职责 |
| --- | --- |
| `file-bridge-core` | 领域模型、服务接口、存储 SPI、仓储端口、安全扩展点 |
| `file-bridge-upload` | 普通上传、分片、续传、秒传、完成、清理和对账流程 |
| `file-bridge-persistence-jdbc` | MySQL JDBC 仓储和事务适配 |
| `file-bridge-storage-local` | 本地流式对象操作和本地分片合并 |
| `file-bridge-storage-minio` | MinIO 对象及原生分片操作 |
| `file-bridge-storage-aliyun` | 阿里云 OSS 对象及原生分片操作 |
| `file-bridge-storage-tencent` | 腾讯云 COS 对象及原生分片操作 |
| `file-bridge-spring-boot-autoconfigure` | 属性绑定、条件装配、覆盖机制和启动校验 |
| `file-bridge-spring-boot-starter` | Spring Boot 接入入口，不传递云 SDK |
| `example` | 独立 Spring Boot 接入工程，包含参考 REST、Demo 身份和上传页面 |

## 3. 依赖方向

```text
core
├── upload
├── persistence-jdbc
├── storage-local
├── storage-minio
├── storage-aliyun
└── storage-tencent

autoconfigure -> core + upload + persistence-jdbc + storage-local
starter -> autoconfigure

example（独立 Parent） -> starter
```

核心模块不反向依赖 Web、Spring 或具体存储 SDK。

`example` 不继承 FileBridge Parent，也不依赖任何内部实现模块。它只通过 Starter 接入，用来验证公开集成边界是否足够。

## 4. 为什么必须有存储实例

FileBridge 将“文件正文”和“业务元数据”分开保存：

```text
MySQL
├── 物理对象定位信息
├── 文件大小和 SHA-256
├── 业务引用
├── 上传任务
└── 分片状态

StorageProvider
└── 真正的文件正文和临时分片
```

因此只配置数据库无法完成文件服务。上传时必须有地方写正文，下载时必须能按历史 `storageId` 找回对象。

`default-storage` 只决定新对象写入哪里。历史对象不会跟随默认值切换，而是始终通过自身记录的 `storageId` 定位。

## 5. 物理对象与业务引用

一个物理对象可以被多个业务文件引用：

```text
fb_storage_object
       ▲
       │ object_id
       ├──────── fb_file_reference A
       ├──────── fb_file_reference B
       └──────── fb_file_reference C
```

带来的行为：

- 对外使用 `fileId`，它对应业务引用；
- 删除一个引用不会破坏其他引用；
- 最后一个引用删除后，物理对象进入延迟回收；
- 秒传只是创建新的业务引用，不把其他用户的 `fileId` 直接返回。

## 6. 普通上传数据流

```text
请求输入流
  -> 可信身份、权限、配额检查
  -> 服务端生成 objectKey
  -> StorageProvider 流式写入并计算 SHA-256
  -> 事务创建物理对象和业务引用
  -> 返回 fileId
```

如果物理对象写入成功但数据库事务失败，服务会尝试补偿删除对象，并保留补偿异常供排查。

## 7. 分片上传数据流

```text
初始化任务
  -> 根据存储能力确定分片规则
  -> 客户端乱序上传分片
  -> 存储确认后记录 COMPLETED 分片
  -> complete 请求提交后台处理
  -> 数据库租约竞争唯一完成权
  -> 平台合并或本地流式合并
  -> 回读最终对象计算大小和 SHA-256
  -> 事务发布对象、引用和 fileId
```

不能通过拼接分片 SHA-256 得到完整文件 SHA-256。最终摘要必须来自最终对象的可信服务端读取流程。

## 8. 状态与并发

主流程：

```text
CREATED -> UPLOADING -> COMPLETING -> VERIFYING -> COMPLETED
```

终态还包括：

- `CANCELLED`
- `EXPIRED`
- `FAILED`

并发完成、取消和清理依赖：

- 数据库唯一约束；
- 带状态条件的 `UPDATE`；
- `lease_owner` 和 `lease_until`；
- 乐观锁版本字段。

本机 `synchronized` 或内存 Map 无法解决多实例竞争，因此不用于生产协调。

## 9. 安全边界

- `CurrentActorProvider` 提供可信租户和用户身份；
- `FileAccessPolicy` 决定上传、读取、删除、任务操作和秒传复用权限；
- Controller 不接收可信身份字段；
- 无权访问具体资源时按不存在处理，避免信息泄露；
- `objectKey` 由服务端生成，不允许客户端指定任意路径；
- 客户端 MIME、扩展名和摘要只作辅助信息；
- 签名 URL 只在业务鉴权通过后生成。

## 10. 自动配置边界

自动配置负责：

- 绑定 `file-bridge.*` 属性；
- 创建本地或显式引入的云存储适配器；
- 创建 JDBC 仓储和业务服务；
- 允许宿主 Bean 覆盖默认实现；
- 启动时校验默认存储和安全扩展点。

自动配置不负责：

- 创建用户或登录系统；
- 默认放行文件权限；
- 自动删除或重建数据库表；
- 注册 REST Controller；
- 自动引入所有云 SDK；
- 自动迁移已有文件到新存储。
