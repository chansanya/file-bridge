import { ref, reactive } from 'vue';
import { Sha256 } from './sha256';
import {
  useOperationLog,
  type OperationLogCategory,
  type OperationLogContext,
  type OperationLogLevel,
} from './useOperationLog';

export interface FriendlyError {
  title: string;
  detail: string;
  isWarning: boolean;
}

export function translateError(err: any): FriendlyError {
  const code = (err?.code || err?.errorCode || "").toUpperCase();
  const msg = (err?.message || err?.errorMessage || "").toString();

  if (
    code === "FILE_TOO_LARGE" ||
    msg.includes("File exceeds configured size limit") ||
    msg.includes("FILE_TOO_LARGE") ||
    msg.includes("413")
  ) {
    return {
      title: "文件超出大小配额",
      detail: "该文件体积超出了服务端配置的单文件上传上限 (可调整 application.yml 中的 max-file-size)",
      isWarning: true,
    };
  }

  if (code === "FILE_NOT_FOUND" || msg.includes("FILE_NOT_FOUND") || msg.includes("File not found")) {
    return {
      title: "文件不存在",
      detail: "所请求的文件不存在或已被业务方删除",
      isWarning: true,
    };
  }

  if (code === "UPLOAD_EXPIRED" || msg.includes("UPLOAD_EXPIRED")) {
    return {
      title: "上传任务已过期",
      detail: "当前分片上传任务已超过系统有效时长，请重新选择文件上传",
      isWarning: true,
    };
  }

  if (code === "CHECKSUM_MISMATCH" || msg.includes("CHECKSUM_MISMATCH")) {
    return {
      title: "SHA-256 摘要不匹配",
      detail: "文件数据在传输过程中可能被损坏或不完整，服务端最终校验未通过",
      isWarning: false,
    };
  }

  if (code === "UPLOAD_PART_CONFLICT" || msg.includes("UPLOAD_PART_CONFLICT")) {
    return {
      title: "分片内容冲突",
      detail: "相同序号的分片重复上传了不同的数据内容",
      isWarning: true,
    };
  }

  if (
    code === "CAPABILITY_NOT_SUPPORTED" ||
    msg.includes("Storage does not support signed URLs") ||
    msg.includes("501")
  ) {
    return {
      title: "存储能力受限",
      detail: "当前存储实例为本地磁盘文件系统，不支持生成临时签名直链",
      isWarning: true,
    };
  }

  if (code === "ACCESS_DENIED" || msg.includes("ACCESS_DENIED")) {
    return {
      title: "访问权限不足",
      detail: "当前用户无权对该文件执行此操作",
      isWarning: true,
    };
  }

  if (code === "IDEMPOTENCY_CONFLICT" || msg.includes("IDEMPOTENCY_CONFLICT")) {
    return {
      title: "幂等键冲突",
      detail: "相同的幂等键被重复用于不同参数的请求",
      isWarning: true,
    };
  }

  return {
    title: "传输遇到阻碍",
    detail: msg || "网络传输中断或服务暂时不可用，请稍后重试",
    isWarning: false,
  };
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly requestId: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface TrackedFetchOptions {
  label: string;
  fallback: string;
  category: OperationLogCategory;
  context?: OperationLogContext;
  quietSuccess?: boolean;
  quietFailure?: boolean;
  failureLevel?: Extract<OperationLogLevel, 'WARN' | 'ERROR'>;
}

export interface FileMetadata {
  fileId: string;
  originalName: string;
  size: number;
  sha256: string;
  contentType: string;
  status: string;
  createdAt: string;
}

export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  items: T[];
}

export interface UploadItem {
  id: string;
  rawFile: File;
  name: string;
  size: number;
  status:
    | 'IDLE'
    | 'HASHING'
    | 'INITIALIZING'
    | 'INSTANT_HIT'
    | 'UPLOADING'
    | 'PAUSED'
    | 'COMPLETING'
    | 'SUCCESS'
    | 'ERROR'
    | 'CANCELLED';
  progress: number;
  speed: string;
  totalParts: number;
  completedParts: Set<number>;
  paused: boolean;
  aborted: boolean;
  worker: Worker | null;
  xhr: XMLHttpRequest | null;
  fileId: string | null;
  uploadId: string | null;
  partSize?: number;
  hashPercent?: number;
  errorMessage?: string | null;
  errorCode?: string | null;
}

export interface DownloadTaskState {
  fileId: string;
  name: string;
  size: number;
  mode: 'stream' | 'chunked';
  isDownloading: boolean;
  progress: number;
  speed: string;
  completedParts: Set<number>;
  totalParts: number;
  error?: string | null;
}

export interface UseFileBridgeOptions {
  apiBase?: string;
  onUploadError?: (item: UploadItem, error: any) => void;
  onUploadSuccess?: (item: UploadItem) => void;
}

export function useFileBridge(config: string | UseFileBridgeOptions = '/api/file-bridge') {
  const options = typeof config === 'string' ? { apiBase: config } : config;
  const apiBase = options.apiBase || '/api/file-bridge';
  const CHUNK_THRESHOLD = 10 * 1024 * 1024;
  const PREFERRED_PART_SIZE = 8 * 1024 * 1024;
  const HASH_BLOCK_SIZE = 4 * 1024 * 1024;
  const CONCURRENCY = 4;

  const fileQueue = ref<UploadItem[]>([]);
  const isProcessing = ref(false);
  const activeDownloads = ref<DownloadTaskState[]>([]);
  const { appendLog } = useOperationLog();

  async function trackedFetch(
    url: string,
    init: RequestInit,
    options: TrackedFetchOptions,
  ): Promise<Response> {
    const requestId = crypto.randomUUID();
    const headers = new Headers(init.headers);
    headers.set('X-Request-Id', requestId);
    const startedAt = performance.now();
    try {
      const response = await fetch(url, { ...init, headers });
      const resolvedRequestId = response.headers.get('X-Request-Id') || requestId;
      const durationMs = Math.round(performance.now() - startedAt);
      if (!response.ok) {
        const error = await toApiError(response, options.fallback, resolvedRequestId);
        if (!options.quietFailure) logApiFailure(options, error, durationMs);
        throw error;
      }
      if (!options.quietSuccess) {
        appendLog({
          level: 'DEBUG',
          category: 'HTTP',
          message: `${options.label}完成`,
          ...options.context,
          requestId: resolvedRequestId,
          httpStatus: response.status,
          durationMs,
        });
      }
      return response;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      const durationMs = Math.round(performance.now() - startedAt);
      const apiError = new ApiError(options.fallback, 0, 'NETWORK_ERROR', requestId);
      if (!options.quietFailure) logApiFailure(options, apiError, durationMs);
      throw apiError;
    }
  }

  function logApiFailure(options: TrackedFetchOptions, error: ApiError, durationMs: number): void {
    const isNet = error.code === 'NETWORK_ERROR';
    const msg = isNet
      ? `${options.label}遇到短暂网络抖动 (${durationMs}ms)`
      : `${options.label}未通过：${error.message}`;
    appendLog({
      level: options.failureLevel || (isNet ? 'WARN' : 'ERROR'),
      category: options.category,
      message: msg,
      ...options.context,
      requestId: error.requestId,
      httpStatus: error.status || undefined,
      durationMs,
    });
  }

  // === 资产列表查询与删除 ===
  async function fetchFileList(
    page = 1,
    size = 10,
    name?: string,
  ): Promise<PageResult<FileMetadata>> {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    });
    if (name && name.trim()) {
      params.append('name', name.trim());
    }
    const res = await trackedFetch(
      `${apiBase}/files?${params.toString()}`,
      {},
      { label: '文件列表请求', fallback: '获取文件列表失败', category: 'FILE' },
    );
    return res.json();
  }

  async function deleteFile(fileId: string): Promise<void> {
    const res = await trackedFetch(
      `${apiBase}/files/${fileId}`,
      { method: 'DELETE' },
      {
        label: '删除文件请求',
        fallback: '删除文件失败',
        category: 'FILE',
        context: { fileId },
        quietSuccess: true,
      },
    );
    appendLog({
      level: 'INFO',
      category: 'FILE',
      message: '文件引用已删除',
      fileId,
      requestId: responseRequestId(res),
      httpStatus: res.status,
    });
  }

  function getDownloadUrl(fileId: string): string {
    return `${apiBase}/files/${fileId}/download`;
  }

  async function createAccessUrl(fileId: string, validitySeconds = 300): Promise<string> {
    const res = await trackedFetch(
      `${apiBase}/files/${fileId}/access-url`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ validitySeconds }),
      },
      {
        label: '临时访问地址请求',
        fallback: '生成访问地址失败',
        category: 'FILE',
        context: { fileId },
        quietSuccess: true,
      },
    );
    const data = await res.json();
    appendLog({
      level: 'INFO',
      category: 'FILE',
      message: `临时访问地址已生成，有效期 ${validitySeconds} 秒`,
      fileId,
      requestId: responseRequestId(res),
      httpStatus: res.status,
    });
    return data.url;
  }

  // === 哈希计算调度 ===
  async function fallbackComputeHash(item: UploadItem): Promise<string> {
    const hasher = new Sha256();
    let offset = 0;
    while (offset < item.size) {
      if (item.aborted) throw new Error('ABORTED');
      const slice = item.rawFile.slice(offset, Math.min(item.size, offset + HASH_BLOCK_SIZE));
      const buffer = await slice.arrayBuffer();
      hasher.update(buffer);
      offset += slice.size;
      item.hashPercent = Math.round((offset / item.size) * 100);
      item.progress = Math.min(15, Math.round((offset / item.size) * 15));
      await new Promise((r) => requestAnimationFrame(r));
    }
    return hasher.digest();
  }

  function computeHashWithWorker(item: UploadItem): Promise<string> {
    if (typeof Worker === 'undefined') {
      appendLog({
        level: 'WARN',
        category: 'UPLOAD',
        message: `浏览器不支持 Web Worker，改用主线程计算摘要：${item.name}`,
        bytes: item.size,
      });
      return fallbackComputeHash(item);
    }
    return new Promise((resolve, reject) => {
      let worker: Worker | null = null;
      const cleanup = () => {
        if (worker) {
          worker.terminate();
          item.worker = null;
          worker = null;
        }
      };
      try {
        worker = new Worker(new URL('./hash-worker.ts', import.meta.url), { type: 'module' });
        item.worker = worker;
        worker.onmessage = (event) => {
          if (event.data.type === 'PROGRESS') {
            item.hashPercent = Math.round(event.data.progress * 100);
            item.progress = Math.min(15, Math.round(event.data.progress * 15));
          } else if (event.data.type === 'DONE') {
            item.hashPercent = 100;
            cleanup();
            resolve(event.data.hash);
          }
        };
        worker.onerror = () => {
          cleanup();
          appendLog({
            level: 'WARN',
            category: 'UPLOAD',
            message: `摘要 Worker 运行失败，改用主线程计算：${item.name}`,
            bytes: item.size,
          });
          fallbackComputeHash(item).then(resolve).catch(reject);
        };
        worker.postMessage({ file: item.rawFile, blockSize: HASH_BLOCK_SIZE });
      } catch {
        cleanup();
        appendLog({
          level: 'WARN',
          category: 'UPLOAD',
          message: `摘要 Worker 创建失败，改用主线程计算：${item.name}`,
          bytes: item.size,
        });
        fallbackComputeHash(item).then(resolve).catch(reject);
      }
    });
  }

  // === 小文件直传 ===
  async function uploadDirect(item: UploadItem): Promise<void> {
    const formData = new FormData();
    formData.append('file', item.rawFile);

    const xhr = new XMLHttpRequest();
    item.xhr = xhr;
    const requestId = crypto.randomUUID();
    const startedAt = performance.now();
    appendLog({
      level: 'INFO',
      category: 'UPLOAD',
      message: `普通上传开始：${item.name}`,
      requestId,
      bytes: item.size,
    });

    await new Promise<void>((resolve, reject) => {
      xhr.open('POST', `${apiBase}/files`);
      xhr.setRequestHeader('Idempotency-Key', crypto.randomUUID());
      xhr.setRequestHeader('X-Request-Id', requestId);

      let lastLoaded = 0;
      let lastTime = performance.now();

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const now = performance.now();
          if (now - lastTime > 60 || e.loaded >= e.total) {
            const bps = ((e.loaded - lastLoaded) / (now - lastTime)) * 1000;
            item.speed = `${(bps / (1024 * 1024)).toFixed(1)} MB/s`;
            lastLoaded = e.loaded;
            lastTime = now;
          }
          item.progress = Math.round((e.loaded / e.total) * 100);
        }
      };

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            const data = JSON.parse(xhr.responseText);
            item.fileId = data.fileId;
            item.progress = 100;
            item.status = 'SUCCESS';
            appendLog({
              level: 'INFO',
              category: 'UPLOAD',
              message: `普通上传完成：${item.name}`,
              requestId: xhr.getResponseHeader('X-Request-Id') || requestId,
              fileId: data.fileId,
              httpStatus: xhr.status,
              durationMs: Math.round(performance.now() - startedAt),
              bytes: item.size,
            });
            resolve();
          } catch {
            const error = new ApiError(
              '服务端返回无效 JSON',
              xhr.status,
              'INVALID_RESPONSE',
              xhr.getResponseHeader('X-Request-Id') || requestId,
            );
            logApiFailure(
              {
                label: '普通上传',
                fallback: error.message,
                category: 'UPLOAD',
                context: { bytes: item.size },
              },
              error,
              Math.round(performance.now() - startedAt),
            );
            reject(error);
          }
        } else {
          const error = xhrApiError(xhr, '普通上传失败', requestId);
          logApiFailure(
            {
              label: '普通上传',
              fallback: error.message,
              category: 'UPLOAD',
              context: { bytes: item.size },
            },
            error,
            Math.round(performance.now() - startedAt),
          );
          reject(error);
        }
      };

      xhr.onerror = () => {
        const error = new ApiError('网络传输发生异常', 0, 'NETWORK_ERROR', requestId);
        logApiFailure(
          {
            label: '普通上传',
            fallback: error.message,
            category: 'UPLOAD',
            context: { bytes: item.size },
          },
          error,
          Math.round(performance.now() - startedAt),
        );
        reject(error);
      };
      xhr.onabort = () => reject(new Error('ABORTED'));
      xhr.send(formData);
    });
  }

  // === 大文件分片上传 ===
  async function uploadMultipart(item: UploadItem): Promise<void> {
    const hashStartedAt = performance.now();
    appendLog({
      level: 'INFO',
      category: 'UPLOAD',
      message: `开始计算文件摘要：${item.name}`,
      bytes: item.size,
    });
    const sha256 = await computeHashWithWorker(item);
    appendLog({
      level: 'INFO',
      category: 'UPLOAD',
      message: `文件摘要计算完成：${item.name}`,
      durationMs: Math.round(performance.now() - hashStartedAt),
      bytes: item.size,
    });
    const resumeKey = `fb:${item.size}:${sha256}`;
    let uploadId = localStorage.getItem(resumeKey);
    let partSize = PREFERRED_PART_SIZE;
    let totalParts = Math.ceil(item.size / PREFERRED_PART_SIZE);

    if (uploadId) {
      const resumedUploadId = uploadId;
      try {
        const queryRes = await trackedFetch(
          `${apiBase}/uploads/${resumedUploadId}`,
          {},
          {
            label: '断点任务查询',
            fallback: '断点任务不可用',
            category: 'UPLOAD',
            context: { uploadId: resumedUploadId },
            quietSuccess: true,
            quietFailure: true,
          },
        );
        const detail = await queryRes.json();
        partSize = detail.partSize;
        totalParts = detail.totalParts;
        item.completedParts = new Set(detail.completedParts || []);
        appendLog({
          level: 'INFO',
          category: 'UPLOAD',
          message: `已恢复断点任务：${item.name}，确认 ${item.completedParts.size}/${totalParts} 个分片`,
          requestId: responseRequestId(queryRes),
          uploadId: resumedUploadId,
          progress: Math.round((item.completedParts.size / totalParts) * 100),
          httpStatus: queryRes.status,
          bytes: item.size,
        });
      } catch (error) {
        uploadId = null;
        localStorage.removeItem(resumeKey);
        appendLog({
          level: 'WARN',
          category: 'UPLOAD',
          message: `旧断点任务不可继续，将重新初始化：${item.name}`,
          uploadId: resumedUploadId,
          ...errorContext(error),
        });
      }
    }

    if (!uploadId) {
      item.status = 'INITIALIZING';
      const initRes = await trackedFetch(
        `${apiBase}/uploads`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': crypto.randomUUID(),
          },
          body: JSON.stringify({
            originalName: item.name,
            size: item.size,
            sha256,
            contentType: item.rawFile.type || 'application/octet-stream',
          }),
        },
        {
          label: '分片任务初始化',
          fallback: '初始化分片任务失败',
          category: 'UPLOAD',
          context: { bytes: item.size },
          quietSuccess: true,
        },
      );
      const initData = await initRes.json();

      if (initData.mode === 'INSTANT') {
        item.fileId = initData.fileId;
        item.progress = 100;
        item.status = 'INSTANT_HIT';
        appendLog({
          level: 'INFO',
          category: 'UPLOAD',
          message: `秒传命中：${item.name}`,
          requestId: responseRequestId(initRes),
          fileId: initData.fileId,
          httpStatus: initRes.status,
          bytes: item.size,
        });
        return;
      }

      uploadId = initData.uploadId;
      partSize = initData.partSize;
      totalParts = initData.totalParts;
      localStorage.setItem(resumeKey, uploadId!);
      appendLog({
        level: 'INFO',
        category: 'UPLOAD',
        message: `分片任务已初始化：${item.name}，共 ${totalParts} 片`,
        requestId: responseRequestId(initRes),
        uploadId: uploadId!,
        httpStatus: initRes.status,
        bytes: item.size,
      });
    }

    item.uploadId = uploadId;
    item.partSize = partSize;
    item.totalParts = totalParts;
    item.status = 'UPLOADING';

    const pendingParts: number[] = [];
    for (let p = 1; p <= totalParts; p++) {
      if (!item.completedParts.has(p)) pendingParts.push(p);
    }

    let loadedBytes = item.completedParts.size * partSize;
    let lastTime = performance.now();
    let lastBytes = loadedBytes;
    const reportedMilestones = new Set<number>();
    markPassedMilestones(item.completedParts.size, item.totalParts, reportedMilestones);

    const worker = async () => {
      while (pendingParts.length > 0) {
        if (item.aborted) throw new Error('ABORTED');
        while (item.paused) {
          await new Promise((r) => setTimeout(r, 250));
          if (item.aborted) throw new Error('ABORTED');
        }

        const partNumber = pendingParts.shift();
        if (!partNumber) break;

        const start = (partNumber - 1) * partSize;
        const end = Math.min(item.size, start + partSize);
        const chunkBlob = item.rawFile.slice(start, end);

        let partSuccess = false;
        let attempts = 0;
        const maxPartAttempts = 3;

        while (!partSuccess && attempts < maxPartAttempts) {
          attempts++;
          try {
            await trackedFetch(
              `${apiBase}/uploads/${item.uploadId}/parts/${partNumber}`,
              {
                method: 'PUT',
                headers: { 'Content-Type': 'application/octet-stream' },
                body: chunkBlob,
              },
              {
                label: `分片 #${partNumber} 上传`,
                fallback: `分片 #${partNumber} 上传失败`,
                category: 'UPLOAD',
                context: {
                  uploadId: item.uploadId || undefined,
                  partNumber,
                  bytes: chunkBlob.size,
                },
                quietSuccess: true,
                quietFailure: attempts < maxPartAttempts,
              },
            );
            partSuccess = true;
          } catch (error) {
            if (item.aborted) throw new Error('ABORTED');
            if (attempts >= maxPartAttempts) {
              pendingParts.unshift(partNumber);
              throw error;
            }
            await new Promise((r) => setTimeout(r, attempts * 300));
          }
        }

        item.completedParts.add(partNumber);
        item.completedParts = new Set(item.completedParts);
        loadedBytes += chunkBlob.size;

        const now = performance.now();
        if (now - lastTime > 60 || loadedBytes >= item.size) {
          const bps = ((loadedBytes - lastBytes) / (now - lastTime)) * 1000;
          item.speed = `${(bps / (1024 * 1024)).toFixed(1)} MB/s`;
          lastTime = now;
          lastBytes = loadedBytes;
        }

        item.progress = 15 + Math.round((item.completedParts.size / item.totalParts) * 80);
        reportProgressMilestones(item, reportedMilestones);
      }
    };

    const workerCount = Math.min(CONCURRENCY, pendingParts.length || 1);
    await Promise.all(Array.from({ length: workerCount }, worker));

    item.status = 'COMPLETING';
    const compRes = await trackedFetch(
      `${apiBase}/uploads/${item.uploadId}/complete`,
      { method: 'POST' },
      {
        label: '分片合并请求',
        fallback: '合并校验请求失败',
        category: 'UPLOAD',
        context: { uploadId: item.uploadId || undefined },
        quietSuccess: true,
      },
    );
    appendLog({
      level: 'INFO',
      category: 'UPLOAD',
      message: `已提交服务端合并与校验：${item.name}`,
      requestId: responseRequestId(compRes),
      uploadId: item.uploadId || undefined,
      httpStatus: compRes.status,
    });

    let compData = await compRes.json();
    let lastCompletionStatus = '';
    while (compData.status === 'COMPLETING' || compData.status === 'VERIFYING') {
      if (compData.status !== lastCompletionStatus) {
        lastCompletionStatus = compData.status;
        appendLog({
          level: 'INFO',
          category: 'UPLOAD',
          message:
            compData.status === 'VERIFYING'
              ? `服务端正在校验最终对象：${item.name}`
              : `服务端正在合并分片：${item.name}`,
          uploadId: item.uploadId || undefined,
        });
      }
      await new Promise((r) => setTimeout(r, 1000));
      if (item.aborted) throw new Error('ABORTED');
      const pollRes = await trackedFetch(
        `${apiBase}/uploads/${item.uploadId}`,
        {},
        {
          label: '合并状态查询',
          fallback: '查询合并状态失败',
          category: 'UPLOAD',
          context: { uploadId: item.uploadId || undefined },
          quietSuccess: true,
        },
      );
      compData = await pollRes.json();
      if (compData.status === 'FAILED') throw new Error('服务端对象合并或摘要校验失败');
    }

    if (compData.status !== 'COMPLETED' || !compData.fileId) {
      throw new Error(`服务端返回了不可发布的任务状态：${compData.status || 'UNKNOWN'}`);
    }

    localStorage.removeItem(resumeKey);
    item.fileId = compData.fileId;
    item.progress = 100;
    item.status = 'SUCCESS';
    appendLog({
      level: 'INFO',
      category: 'UPLOAD',
      message: `分片上传完成：${item.name}`,
      fileId: compData.fileId,
      uploadId: item.uploadId || undefined,
      progress: 100,
      bytes: item.size,
    });
  }

  async function processItem(item: UploadItem): Promise<void> {
    try {
      if (item.size <= CHUNK_THRESHOLD) {
        item.status = 'UPLOADING';
        await uploadDirect(item);
      } else {
        item.status = 'HASHING';
        await uploadMultipart(item);
      }
    } catch (err: any) {
      if (item.aborted) {
        item.status = 'CANCELLED';
      } else {
        item.status = 'ERROR';
        item.errorMessage = err.message || '上传异常中断';
        item.errorCode = err instanceof ApiError ? err.code : (err.code || 'UNKNOWN');
        if (options.onUploadError) {
          options.onUploadError(item, err);
        }
        if (!(err instanceof ApiError)) {
          appendLog({
            level: 'ERROR',
            category: 'UPLOAD',
            message: `上传任务失败：${item.name}，${item.errorMessage}`,
            fileId: item.fileId || undefined,
            uploadId: item.uploadId || undefined,
            bytes: item.size,
          });
        }
      }
    }
  }

  async function processQueue(): Promise<void> {
    if (isProcessing.value) return;
    isProcessing.value = true;
    while (true) {
      const next = fileQueue.value.find((item) => item.status === 'IDLE');
      if (!next) break;
      await processItem(next);
    }
    isProcessing.value = false;
  }

  function addFiles(files: FileList | File[]): void {
    const list = Array.from(files);
    for (const file of list) {
      fileQueue.value.unshift({
        id: crypto.randomUUID(),
        rawFile: file,
        name: file.name,
        size: file.size,
        status: 'IDLE',
        progress: 0,
        speed: '0.0 MB/s',
        totalParts: Math.ceil(file.size / PREFERRED_PART_SIZE),
        completedParts: new Set(),
        paused: false,
        aborted: false,
        worker: null,
        xhr: null,
        fileId: null,
        uploadId: null,
        hashPercent: 0,
        errorMessage: null,
      });
    }
    appendLog({
      level: 'INFO',
      category: 'UPLOAD',
      message: `已加入 ${list.length} 个文件到上传队列`,
      bytes: list.reduce((total, file) => total + file.size, 0),
    });
    processQueue();
  }

  function retryUpload(item: UploadItem): void {
    if (item.status === 'ERROR' || item.status === 'PAUSED' || item.status === 'CANCELLED') {
      item.aborted = false;
      item.paused = false;
      item.errorMessage = null;
      item.status = 'IDLE';
      appendLog({
        level: 'INFO',
        category: 'UPLOAD',
        message: `正在继续断点续传：${item.name}（已确认 ${item.completedParts ? item.completedParts.size : 0} 片）`,
        uploadId: item.uploadId || undefined,
        progress: item.progress,
      });
      processQueue();
    }
  }

  function pauseUpload(item: UploadItem): void {
    if (item.status === 'UPLOADING') {
      item.paused = true;
      item.status = 'PAUSED';
      appendLog({
        level: 'INFO',
        category: 'UPLOAD',
        message: `上传已暂停：${item.name}`,
        uploadId: item.uploadId || undefined,
        progress: item.progress,
      });
    }
  }

  function resumeUpload(item: UploadItem): void {
    if (item.status === 'PAUSED') {
      item.paused = false;
      item.status = 'UPLOADING';
      appendLog({
        level: 'INFO',
        category: 'UPLOAD',
        message: `上传已恢复：${item.name}`,
        uploadId: item.uploadId || undefined,
        progress: item.progress,
      });
      if (!isProcessing.value) processQueue();
    }
  }

  async function cancelUpload(item: UploadItem): Promise<void> {
    item.aborted = true;
    if (item.worker) {
      item.worker.terminate();
      item.worker = null;
    }
    if (item.xhr) {
      item.xhr.abort();
    }
    if (item.uploadId) {
      try {
        await trackedFetch(
          `${apiBase}/uploads/${item.uploadId}`,
          { method: 'DELETE' },
          {
            label: '取消分片任务',
            fallback: '取消分片任务失败',
            category: 'UPLOAD',
            context: { uploadId: item.uploadId },
            quietSuccess: true,
            failureLevel: 'WARN',
          },
        );
      } catch {
        // 服务端取消失败已经写入操作日志，本地队列仍按用户意图移除。
      }
    }
    item.status = 'CANCELLED';
    appendLog({
      level: 'INFO',
      category: 'UPLOAD',
      message: `上传任务已取消：${item.name}`,
      uploadId: item.uploadId || undefined,
      progress: item.progress,
    });
    const index = fileQueue.value.indexOf(item);
    if (index > -1) fileQueue.value.splice(index, 1);
  }

  // === 下载调度 (普通流式 / 并发分片) ===
  async function downloadFile(
    fileId: string,
    fileName: string,
    fileSize: number,
    options: {
      chunked?: boolean;
      concurrency?: number;
      partSize?: number;
    } = {},
  ): Promise<void> {
    const isChunked = options.chunked !== false;
    const concurrency = options.concurrency || 4;
    const partSize = options.partSize || 8 * 1024 * 1024;
    const useChunkedDownload = isChunked && fileSize > partSize;
    const url = getDownloadUrl(fileId);

    const task = reactive<DownloadTaskState>({
      fileId,
      name: fileName,
      size: fileSize,
      mode: useChunkedDownload ? 'chunked' : 'stream',
      isDownloading: true,
      progress: 0,
      speed: '0.0 MB/s',
      completedParts: new Set(),
      totalParts: useChunkedDownload ? Math.ceil(fileSize / partSize) : 1,
      error: null,
    });
    activeDownloads.value.push(task);
    appendLog({
      level: 'INFO',
      category: 'DOWNLOAD',
      message: `${task.mode === 'chunked' ? 'Range 分片下载' : '普通流式下载'}开始：${fileName}`,
      fileId,
      bytes: fileSize,
    });
    const reportedMilestones = new Set<number>();

    try {
      if (!useChunkedDownload) {
        const response = await trackedFetch(
          url,
          {},
          {
            label: '普通下载请求',
            fallback: '下载请求失败',
            category: 'DOWNLOAD',
            context: { fileId, bytes: fileSize },
            quietSuccess: true,
          },
        );
        const total = Number(response.headers.get('Content-Length')) || fileSize;
        const reader = response.body?.getReader();
        if (!reader) throw new Error('流读取不可用');

        const chunks: Uint8Array[] = [];
        let received = 0;
        let lastTime = performance.now();
        let lastBytes = 0;

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          chunks.push(value);
          received += value.length;

          const now = performance.now();
          if (now - lastTime > 60 || received >= fileSize) {
            const bps = ((received - lastBytes) / (now - lastTime)) * 1000;
            task.speed = `${(bps / (1024 * 1024)).toFixed(1)} MB/s`;
            lastTime = now;
            lastBytes = received;
          }
          task.progress = Math.min(100, Math.round((received / total) * 100));
          reportDownloadMilestones(task, reportedMilestones);
        }

        const blob = new Blob(chunks, {
          type: response.headers.get('Content-Type') || 'application/octet-stream',
        });
        saveBlob(blob, fileName);
      } else {
        const totalParts = Math.ceil(fileSize / partSize);
        task.totalParts = totalParts;
        const partsBuffer = new Array<ArrayBuffer>(totalParts);
        const queue: number[] = [];
        for (let p = 1; p <= totalParts; p++) queue.push(p);

        let receivedBytes = 0;
        let lastTime = performance.now();
        let lastBytes = 0;

        const downloadWorker = async () => {
          while (queue.length > 0) {
            const partNum = queue.shift();
            if (!partNum) break;
            const start = (partNum - 1) * partSize;
            const end = Math.min(fileSize - 1, start + partSize - 1);

            let res: Response;
            try {
              res = await trackedFetch(
                url,
                { headers: { Range: `bytes=${start}-${end}` } },
                {
                  label: `下载分片 #${partNum}`,
                  fallback: `分片 #${partNum} 下载失败`,
                  category: 'DOWNLOAD',
                  context: { fileId, partNumber: partNum },
                  quietSuccess: true,
                },
              );
            } catch (error) {
              queue.unshift(partNum);
              throw error;
            }

            const buf = await res.arrayBuffer();
            partsBuffer[partNum - 1] = buf;
            task.completedParts.add(partNum);
            task.completedParts = new Set(task.completedParts);
            receivedBytes += buf.byteLength;

            const now = performance.now();
            if (now - lastTime > 60 || receivedBytes >= fileSize) {
              const bps = ((receivedBytes - lastBytes) / (now - lastTime)) * 1000;
              task.speed = `${(bps / (1024 * 1024)).toFixed(1)} MB/s`;
              lastTime = now;
              lastBytes = receivedBytes;
            }

            task.progress = Math.min(100, Math.round((receivedBytes / fileSize) * 100));
            reportDownloadMilestones(task, reportedMilestones);
          }
        };

        const activeWorkers = Math.min(concurrency, queue.length);
        await Promise.all(Array.from({ length: activeWorkers }, downloadWorker));

        const fullBlob = new Blob(partsBuffer, { type: 'application/octet-stream' });
        saveBlob(fullBlob, fileName);
      }
      task.progress = 100;
      appendLog({
        level: 'INFO',
        category: 'DOWNLOAD',
        message: `文件下载完成：${fileName}`,
        fileId,
        progress: 100,
        bytes: fileSize,
      });
    } catch (err: any) {
      task.error = err.message || '下载过程异常中断';
      if (!(err instanceof ApiError)) {
        appendLog({
          level: 'ERROR',
          category: 'DOWNLOAD',
          message: `文件下载失败：${fileName}，${task.error}`,
          fileId,
          progress: task.progress,
          bytes: fileSize,
        });
      }
      throw err;
    } finally {
      task.isDownloading = false;
      setTimeout(() => {
        const idx = activeDownloads.value.indexOf(task);
        if (idx > -1) activeDownloads.value.splice(idx, 1);
      }, 3500);
    }
  }

  function saveBlob(blob: Blob, filename: string): void {
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = filename || 'downloaded-file';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(blobUrl), 10000);
  }

  function markPassedMilestones(
    completedParts: number,
    totalParts: number,
    reported: Set<number>,
  ): void {
    const progress = totalParts === 0 ? 0 : Math.round((completedParts / totalParts) * 100);
    for (const milestone of [25, 50, 75, 100]) {
      if (progress >= milestone) reported.add(milestone);
    }
  }

  function reportProgressMilestones(item: UploadItem, reported: Set<number>): void {
    const progress = Math.round((item.completedParts.size / item.totalParts) * 100);
    for (const milestone of [25, 50, 75, 100]) {
      if (progress >= milestone && !reported.has(milestone)) {
        reported.add(milestone);
        appendLog({
          level: 'INFO',
          category: 'UPLOAD',
          message: `分片上传进度达到 ${milestone}%：${item.name}`,
          uploadId: item.uploadId || undefined,
          progress: milestone,
          bytes: item.size,
        });
      }
    }
  }

  function reportDownloadMilestones(task: DownloadTaskState, reported: Set<number>): void {
    for (const milestone of [25, 50, 75, 100]) {
      if (task.progress >= milestone && !reported.has(milestone)) {
        reported.add(milestone);
        appendLog({
          level: 'INFO',
          category: 'DOWNLOAD',
          message: `下载进度达到 ${milestone}%：${task.name}`,
          fileId: task.fileId,
          progress: milestone,
          bytes: task.size,
        });
      }
    }
  }

  function responseRequestId(response: Response): string | undefined {
    return response.headers.get('X-Request-Id') || undefined;
  }

  function errorContext(error: unknown): OperationLogContext {
    if (!(error instanceof ApiError)) return {};
    return {
      requestId: error.requestId,
      httpStatus: error.status || undefined,
    };
  }

  function xhrApiError(xhr: XMLHttpRequest, fallback: string, requestId: string): ApiError {
    let code = 'HTTP_ERROR';
    let message = fallback;
    let resolvedRequestId = xhr.getResponseHeader('X-Request-Id') || requestId;
    try {
      const body = JSON.parse(xhr.responseText);
      code = body.code || code;
      message = body.message || message;
      resolvedRequestId = body.requestId || resolvedRequestId;
    } catch {
      // 非 JSON 错误响应只使用状态码和安全回退文案，不写入原始响应正文。
    }
    return new ApiError(message, xhr.status, code, resolvedRequestId);
  }

  async function toApiError(
    response: Response,
    fallback: string,
    requestId = responseRequestId(response) || 'unknown',
  ): Promise<ApiError> {
    try {
      const text = await response.text();
      const body = JSON.parse(text);
      return new ApiError(
        body.message || fallback,
        response.status,
        body.code || 'HTTP_ERROR',
        body.requestId || requestId,
      );
    } catch {
      return new ApiError(fallback, response.status, 'HTTP_ERROR', requestId);
    }
  }

  return {
    fileQueue,
    isProcessing,
    activeDownloads,
    fetchFileList,
    deleteFile,
    getDownloadUrl,
    createAccessUrl,
    addFiles,
    pauseUpload,
    resumeUpload,
    retryUpload,
    cancelUpload,
    downloadFile,
  };
}
