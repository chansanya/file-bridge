# FileBridge 数据库脚本

## 支持范围

- 数据库：MySQL 8.4
- 字符集：`utf8mb4`
- 时间字段：UTC `DATETIME(6)`
- UUID：`CHAR(36)`

## 执行顺序

| 顺序 | 脚本 | 内容 |
| --- | --- | --- |
| 1 | `V1__file_objects_and_references.sql` | 物理对象、业务引用、幂等记录 |
| 2 | `V2__multipart_upload.sql` | 上传任务和分片记录 |
| 3 | `V3__cleanup_and_reconciliation.sql` | 对账问题记录 |
| 4 | `V4__upload_completion_retry.sql` | 完成任务重试、退避和错误记录 |
| 5 | `V5__object_unreferenced_time.sql` | 无引用对象保留时间和删除竞态控制 |
| 6 | `V6__reconciliation_retry_state.sql` | 对账问题指纹、租约、退避和处理结果 |

必须按顺序执行，不要跳过中间版本，也不要修改已经在生产环境执行过的脚本。

## 使用方式

### 手工执行

```bash
mysql -h 127.0.0.1 -u filebridge -p file_bridge \
  < docs/database/V1__file_objects_and_references.sql

mysql -h 127.0.0.1 -u filebridge -p file_bridge \
  < docs/database/V2__multipart_upload.sql

mysql -h 127.0.0.1 -u filebridge -p file_bridge \
  < docs/database/V3__cleanup_and_reconciliation.sql
```

### 使用 Flyway

将脚本复制到宿主项目自己的迁移目录，例如：

```text
src/main/resources/db/migration/file-bridge/
```

然后把该目录加入宿主 Flyway 配置，并确保版本号不会与宿主已有迁移冲突。

Starter 不引入 Flyway，也不会主动执行迁移。独立 `example` 工程为了方便本地运行，显式引入并启用了 Flyway。

## 核心表

| 表 | 用途 |
| --- | --- |
| `fb_storage_object` | 保存物理对象位置、大小、摘要和生命周期状态 |
| `fb_file_reference` | 保存面向租户和用户的业务文件引用 |
| `fb_idempotency_record` | 保存上传和初始化请求的幂等结果 |
| `fb_upload_task` | 保存分片任务、目标存储、状态和租约 |
| `fb_upload_part` | 保存平台已确认的分片信息 |
| `fb_reconciliation_issue` | 保存需要重试或人工判断的一致性问题 |

## 索引说明

`fb_storage_object` 的对象定位唯一索引使用前缀长度：

```sql
UNIQUE KEY uk_fb_object_location(
  storage_id,
  bucket_name(128),
  object_key(512)
)
```

这是为了避免 `utf8mb4` 下复合索引超过 MySQL InnoDB 3072 字节限制。业务层仍保存完整的 Bucket 和对象路径。

## 变更约束

- 新结构必须新增迁移脚本，不能修改已发布迁移；
- 禁止自动删除宿主数据；
- 并发语义必须在 MySQL 上验证，不使用 H2 代替生产数据库结论；
- 执行前必须备份生产数据库并评估锁表影响。
