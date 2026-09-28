# Example REST API

## 1. 基础约定

- 默认前缀：`/api/file-bridge`
- 文件上传：`multipart/form-data`
- 分片上传：`application/octet-stream`
- 其他请求和响应：`application/json`
- 文件下载：原始二进制流，不包装 JSON
- UUID 使用标准字符串格式
- 身份来自宿主可信上下文，接口不接受 `tenantId` 和 `ownerId`

本页描述的是独立 [`example`](../example) 工程提供的参考接口。Starter 本身不注册 REST Controller。

示例路径配置：

```yaml
example:
  file-bridge:
    base-path: /api/file-bridge
```

真实业务工程可以参考示例自行调整接口路径、认证机制和响应协议。

## 2. 状态码约定

| 状态码 | 说明 |
| --- | --- |
| `200 OK` | 查询成功、秒传成功或任务已完成 |
| `201 Created` | 普通上传成功或新建分片任务 |
| `202 Accepted` | 已提交后台合并和最终校验 |
| `204 No Content` | 分片上传成功、取消成功或删除成功 |
| `400 Bad Request` | 参数、摘要、分片序号或长度非法 |
| `401 Unauthorized` | 当前请求没有可信身份 |
| `404 Not Found` | 资源不存在或当前身份无权访问 |
| `409 Conflict` | 幂等键冲突、分片内容冲突或状态冲突 |
| `413 Payload Too Large` | 文件超过限制 |
| `501 Not Implemented` | 当前存储不支持相关能力，例如本地存储签名 URL |
| `503 Service Unavailable` | 存储或数据库故障 |

## 3. 统一错误响应

```json
{
  "code": "UPLOAD_PART_CONFLICT",
  "message": "Part already exists with different content",
  "requestId": "4428dc19-55a0-4a13-bf69-8ad955d497dd"
}
```

响应头同时包含：

```http
X-Request-Id: 4428dc19-55a0-4a13-bf69-8ad955d497dd
```

主要错误码：

| 错误码 | 含义 |
| --- | --- |
| `UNAUTHENTICATED` | 缺少可信身份 |
| `ACCESS_DENIED` | 无权访问资源，对外按 404 处理 |
| `FILE_NOT_FOUND` | 文件不存在或不可用 |
| `UPLOAD_NOT_FOUND` | 上传任务不存在 |
| `FILE_TOO_LARGE` | 文件超过大小限制 |
| `IDEMPOTENCY_CONFLICT` | 同一幂等键对应不同请求 |
| `INVALID_PART` | 分片序号或大小错误 |
| `UPLOAD_PART_CONFLICT` | 同序号已存在不同内容 |
| `UPLOAD_EXPIRED` | 上传任务已过期 |
| `INVALID_UPLOAD_STATE` | 当前状态不允许执行该操作 |
| `CHECKSUM_MISMATCH` | 摘要不一致 |
| `CAPABILITY_NOT_SUPPORTED` | 存储能力不支持 |
| `STORAGE_FAILURE` | 存储调用失败 |

## 4. 普通文件接口

### 4.1 普通上传

```http
POST /api/file-bridge/files
Content-Type: multipart/form-data
Idempotency-Key: optional-key
```

表单字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 文件正文 |
| `businessType` | 否 | 宿主业务类型 |
| `businessId` | 否 | 宿主业务标识 |

成功响应：`201 Created`

```json
{
  "fileId": "9747bcda-b8d3-489f-a087-b9d5524537cc",
  "originalName": "report.pdf",
  "size": 102400,
  "sha256": "64位十六进制摘要",
  "contentType": "application/pdf",
  "status": "ACTIVE",
  "createdAt": "2026-09-28T03:00:00Z"
}
```

幂等规则：

- 同一身份、同一操作、同一 `Idempotency-Key` 和相同请求指纹返回原结果；
- 同一幂等键对应不同请求时返回 `409`；
- 幂等键不跨租户、用户共享。

### 4.2 查询元数据

```http
GET /api/file-bridge/files/{fileId}
```

成功响应：`200 OK`，响应结构与普通上传相同。

### 4.3 下载文件

```http
GET /api/file-bridge/files/{fileId}/download
```

成功响应：`200 OK`

- 正文为文件二进制流；
- `Content-Length` 为文件大小；
- `Content-Type` 为已保存内容类型；
- `Content-Disposition` 使用安全编码后的原始文件名。

### 4.4 创建临时地址

```http
POST /api/file-bridge/files/{fileId}/access-url
Content-Type: application/json
```

请求体：

```json
{
  "validitySeconds": 300
}
```

规则：

- 默认有效期为 300 秒；
- 当前接口将有效期限制在 1～3600 秒；
- 本地存储不支持临时地址，返回 `501`。

成功响应：`200 OK`

```json
{
  "url": "https://storage.example.com/signed-url"
}
```

### 4.5 删除文件引用

```http
DELETE /api/file-bridge/files/{fileId}
```

成功响应：`204 No Content`。

该操作删除业务引用，不会立即删除仍被其他引用使用的物理对象。无引用对象由后台清理任务延迟回收。

## 5. 分片上传接口

### 5.1 初始化任务或检查秒传

```http
POST /api/file-bridge/uploads
Content-Type: application/json
Idempotency-Key: optional-key
```

请求体：

```json
{
  "originalName": "large.zip",
  "size": 1073741824,
  "sha256": "64位十六进制摘要",
  "contentType": "application/zip",
  "businessType": "ARCHIVE",
  "businessId": "order-10001"
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `originalName` | 是 | 原始文件名 |
| `size` | 是 | 文件总字节数，必须大于 0 |
| `sha256` | 否 | 完整文件 SHA-256；提供后才可能命中秒传 |
| `contentType` | 否 | 客户端声明类型，仅作辅助 |
| `businessType` | 否 | 宿主业务类型 |
| `businessId` | 否 | 宿主业务标识 |

秒传成功：`200 OK`

```json
{
  "mode": "INSTANT",
  "fileId": "9747bcda-b8d3-489f-a087-b9d5524537cc",
  "uploadId": null,
  "partSize": 0,
  "totalParts": 0,
  "expiresAt": null
}
```

创建任务成功：`201 Created`

```json
{
  "mode": "UPLOAD",
  "fileId": null,
  "uploadId": "1e22be6a-1661-426f-8190-fe56e777895f",
  "partSize": 8388608,
  "totalParts": 128,
  "expiresAt": "2026-09-29T03:00:00Z"
}
```

### 5.2 上传分片

```http
PUT /api/file-bridge/uploads/{uploadId}/parts/{partNumber}
Content-Type: application/octet-stream
Content-Length: 8388608
X-Part-Sha256: optional-part-sha256
```

规则：

- `partNumber` 从 1 开始；
- 非最后一片必须等于任务返回的 `partSize`；
- 最后一片长度由总大小计算；
- 同序号、同内容重试按幂等成功处理；
- 同序号、不同内容返回 `409`；
- 成功后返回 `204 No Content`。

### 5.3 查询上传状态

```http
GET /api/file-bridge/uploads/{uploadId}
```

成功响应：`200 OK`

```json
{
  "uploadId": "1e22be6a-1661-426f-8190-fe56e777895f",
  "status": "UPLOADING",
  "expectedSize": 1073741824,
  "partSize": 8388608,
  "totalParts": 128,
  "completedParts": [1, 2, 5],
  "fileId": null,
  "expiresAt": "2026-09-29T03:00:00Z"
}
```

客户端恢复上传时，只补传不在 `completedParts` 中的分片。

### 5.4 请求完成

```http
POST /api/file-bridge/uploads/{uploadId}/complete
```

如果已经完成，返回 `200 OK` 和稳定的 `fileId`。

如果后台正在合并或校验，返回 `202 Accepted`：

```json
{
  "uploadId": "1e22be6a-1661-426f-8190-fe56e777895f",
  "status": "COMPLETING",
  "expectedSize": 1073741824,
  "partSize": 8388608,
  "totalParts": 128,
  "completedParts": [1, 2, 3],
  "fileId": null,
  "expiresAt": "2026-09-29T03:00:00Z"
}
```

客户端应继续轮询查询接口，直到状态变为 `COMPLETED` 或终态错误。

### 5.5 取消任务

```http
DELETE /api/file-bridge/uploads/{uploadId}
```

成功响应：`204 No Content`。

已完成任务不会被取消；活动任务取消后会尝试清理平台分片或本地临时目录。

## 6. 上传任务状态

| 状态 | 含义 |
| --- | --- |
| `CREATED` | 任务已创建，尚未确认分片 |
| `UPLOADING` | 正在上传分片 |
| `COMPLETING` | 后台正在合并分片 |
| `VERIFYING` | 正在回读最终对象并校验大小和 SHA-256 |
| `COMPLETED` | 文件已发布，可查询和下载 |
| `CANCELLED` | 任务已取消 |
| `EXPIRED` | 任务已过期 |
| `FAILED` | 完成或校验失败 |
