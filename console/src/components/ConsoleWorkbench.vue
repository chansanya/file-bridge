<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue';
import {
  NCard,
  NDataTable,
  NPagination,
  NInput,
  NButton,
  NTag,
  NTooltip,
  NSpace,
  NBadge,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NSelect,
  useMessage,
  useDialog,
  type DataTableColumns,
} from 'naive-ui';
import {
  HardDrive,
  UploadCloud,
  FileText,
  CheckCircle2,
  AlertTriangle,
  Zap,
  RefreshCw,
  Layers,
  Pause,
  Play,
  Trash2,
  Download,
  HardDriveDownload,
  Link as LinkIcon,
  Copy,
  Server,
  ShieldCheck,
  Activity,
  Search,
  Terminal,
  Bug,
  Info,
  CircleX,
} from 'lucide-vue-next';
import { useFileBridge, translateError, type FileMetadata, type UploadItem } from '../composables/useFileBridge';
import {
  useOperationLog,
  type OperationLogEntry,
  type OperationLogLevel,
} from '../composables/useOperationLog';

const message = useMessage();
const dialog = useDialog();

const {
  fileQueue,
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
} = useFileBridge({
  onUploadError(item, err) {
    const friendly = translateError(err);
    if (friendly.isWarning) {
      message.warning(`文件 “${item.name}” 提示：${friendly.detail}`);
    } else {
      message.error(`文件 “${item.name}” 异常：${friendly.detail}`);
    }
  },
  onUploadSuccess(item) {
    message.success(`文件 “${item.name}” 已成功入库发布`);
    loadTableData();
  },
});

const {
  entries: operationLogs,
  unreadCount,
  unreadErrorCount,
  viewerOpen,
  clearLogs,
  setViewerOpen,
  formatLogs,
} = useOperationLog();

const fileInputRef = ref<HTMLInputElement | null>(null);
const isDragActive = ref(false);
const searchKeyword = ref('');
const currentPage = ref(1);
const pageSize = ref(10);
const isTableLoading = ref(false);
const logLevelFilter = ref<'ALL' | OperationLogLevel>('ALL');

const logDrawerOpen = computed({
  get: () => viewerOpen.value,
  set: (open: boolean) => setViewerOpen(open),
});

const logLevelOptions = [
  { label: '全部级别', value: 'ALL' },
  { label: '调试', value: 'DEBUG' },
  { label: '信息', value: 'INFO' },
  { label: '警告', value: 'WARN' },
  { label: '错误', value: 'ERROR' },
];

const filteredLogs = computed(() => {
  if (logLevelFilter.value === 'ALL') return operationLogs.value;
  return operationLogs.value.filter((entry) => entry.level === logLevelFilter.value);
});

const filePage = ref<{
  total: number;
  page: number;
  size: number;
  items: FileMetadata[];
}>({
  total: 0,
  page: 1,
  size: 10,
  items: [],
});

const formatBytes = (bytes?: number) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
};

const formatTime = (isoString?: string) => {
  if (!isoString) return '-';
  const d = new Date(isoString);
  return d.toLocaleString('zh-CN', { hour12: false });
};

const truncateHash = (hash?: string) => {
  if (!hash) return '-';
  if (hash.length <= 16) return hash;
  return `${hash.slice(0, 8)}...${hash.slice(-8)}`;
};

const totalStorageBytes = computed(() => {
  return filePage.value.items.reduce((acc, curr) => acc + (curr.size || 0), 0);
});

const activeUploadCount = computed(() => {
  return fileQueue.value.filter(
    (i) => i.status === 'UPLOADING' || i.status === 'HASHING' || i.status === 'COMPLETING',
  ).length;
});

const activeThroughput = computed(() => {
  const active = fileQueue.value.find((i) => i.status === 'UPLOADING');
  if (active) return active.speed;
  for (const dl of activeDownloads.value) {
    if (dl.isDownloading) return dl.speed;
  }
  return '0.0 MB/s';
});

const loadTableData = async () => {
  isTableLoading.value = true;
  try {
    const res = await fetchFileList(currentPage.value, pageSize.value, searchKeyword.value);
    filePage.value = res;
  } catch (err: any) {
    message.error(err.message || '加载文件资产列表失败');
  } finally {
    isTableLoading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  loadTableData();
};

const handleResetSearch = () => {
  searchKeyword.value = '';
  currentPage.value = 1;
  loadTableData();
};

const handlePageChange = (page: number) => {
  currentPage.value = page;
  loadTableData();
};

const handlePageSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  loadTableData();
};

const onDrop = (e: DragEvent) => {
  isDragActive.value = false;
  if (e.dataTransfer && e.dataTransfer.files.length) {
    addFiles(e.dataTransfer.files);
    message.info(`已成功加入 ${e.dataTransfer.files.length} 个文件到上传队列`);
  }
};

const onSelectFiles = (e: Event) => {
  const target = e.target as HTMLInputElement;
  if (target.files && target.files.length) {
    addFiles(target.files);
    message.info(`已成功加入 ${target.files.length} 个文件到上传队列`);
    target.value = '';
  }
};

const statusText = (item: UploadItem) => {
  switch (item.status) {
    case 'IDLE':
      return '就绪准备中';
    case 'HASHING':
      return `正在计算特征值 (SHA-256) ${item.hashPercent || 0}%`;
    case 'INITIALIZING':
      return '正在申请分片通道...';
    case 'INSTANT_HIT':
      return '秒传校验通过';
    case 'UPLOADING':
      return `分片上传中 (${item.speed}) [${item.completedParts ? item.completedParts.size : 0}/${item.totalParts}]`;
    case 'PAUSED':
      return '上传已暂停';
    case 'COMPLETING':
      return '服务端校验与合并中';
    case 'SUCCESS':
      return '已成功入库发布';
    case 'ERROR': {
      if (item.completedParts && item.completedParts.size > 0) {
        return `传输暂停 (已保全 ${item.completedParts.size}/${item.totalParts} 片)`;
      }
      const friendly = translateError(item);
      return friendly.title;
    }
    case 'CANCELLED':
      return '任务已取消';
    default:
      return item.status;
  }
};

const downloadNative = (fileId: string, filename: string) => {
  message.info(`已呼起浏览器原生下载引擎接管：“${filename}”（数据直写系统硬盘，零网页内存消耗）`);
  const a = document.createElement('a');
  a.href = getDownloadUrl(fileId);
  a.download = filename || 'downloaded-file';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
};

const copyToClipboard = async (text: string, successHint: string) => {
  try {
    await navigator.clipboard.writeText(text);
    message.success(successHint);
  } catch {
    const el = document.createElement('textarea');
    el.value = text;
    document.body.appendChild(el);
    el.select();
    document.execCommand('copy');
    document.body.removeChild(el);
    message.success(successHint);
  }
};

const copyVisibleLogs = async () => {
  if (filteredLogs.value.length === 0) return;
  await copyToClipboard(formatLogs(filteredLogs.value), '当前日志已复制到剪贴板');
};

const formatLogTime = (timestamp: number) => {
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3,
  });
};

const logContextFields = (entry: OperationLogEntry) => {
  const fields: string[] = [];
  if (entry.requestId) fields.push(`requestId=${entry.requestId}`);
  if (entry.fileId) fields.push(`fileId=${entry.fileId}`);
  if (entry.uploadId) fields.push(`uploadId=${entry.uploadId}`);
  if (entry.partNumber !== undefined) fields.push(`part=${entry.partNumber}`);
  if (entry.progress !== undefined) fields.push(`progress=${entry.progress}%`);
  if (entry.httpStatus !== undefined) fields.push(`status=${entry.httpStatus}`);
  if (entry.durationMs !== undefined) fields.push(`duration=${entry.durationMs}ms`);
  if (entry.bytes !== undefined) fields.push(`bytes=${entry.bytes}`);
  return fields;
};

const levelIcon = (level: OperationLogLevel) => {
  if (level === 'ERROR') return CircleX;
  if (level === 'WARN') return AlertTriangle;
  if (level === 'DEBUG') return Bug;
  return Info;
};

// 严谨规范对齐的表格列定义
const columns: DataTableColumns<FileMetadata> = [
  {
    title: '文件名',
    key: 'originalName',
    align: 'left',
    minWidth: 220,
    render(row) {
      return h('div', { class: 'file-row-flex' }, [
        h('div', { class: 'file-icon-badge' }, [
          h(FileText, { size: 15, class: 'badge-file-icon' }),
        ]),
        h('span', { class: 'file-name-label', title: row.originalName }, row.originalName),
      ]);
    },
  },
  {
    title: '文件大小',
    key: 'size',
    width: 120,
    align: 'right',
    render(row) {
      return h('span', { class: 'table-mono-text' }, formatBytes(row.size));
    },
  },
  {
    title: 'MIME 类型',
    key: 'contentType',
    width: 150,
    align: 'center',
    render(row) {
      return h(
        NTag,
        { size: 'small', bordered: false, type: 'info', class: 'custom-tag-blue' },
        { default: () => row.contentType || 'application/octet-stream' },
      );
    },
  },
  {
    title: '存储节点',
    key: 'storageId',
    width: 130,
    align: 'center',
    render() {
      return h(
        NTag,
        { size: 'small', bordered: false, type: 'success', class: 'custom-tag-green' },
        { default: () => 'local-main' },
      );
    },
  },
  {
    title: 'SHA-256 摘要',
    key: 'sha256',
    width: 210,
    align: 'center',
    render(row) {
      return h('div', { class: 'table-hash-wrap' }, [
        h('span', { class: 'table-mono-text' }, truncateHash(row.sha256)),
        h(
          NTooltip,
          { trigger: 'hover' },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'tiny',
                  quaternary: true,
                  circle: true,
                  class: 'hash-copy-btn',
                  onClick: () => copyToClipboard(row.sha256, '已成功复制完整 SHA-256 摘要至剪贴板'),
                },
                { icon: () => h(Copy, { size: 13 }) },
              ),
            default: () => '复制完整哈希值',
          },
        ),
      ]);
    },
  },
  {
    title: '入库时间',
    key: 'createdAt',
    width: 180,
    align: 'center',
    render(row) {
      return h('span', { class: 'table-date-text' }, formatTime(row.createdAt));
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    fixed: 'right',
    align: 'center',
    render(row) {
      return h(NSpace, { justify: 'center', align: 'center', size: 6 }, () => [
        h(
          NTooltip,
          { trigger: 'hover' },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'small',
                  circle: true,
                  secondary: true,
                  type: 'success',
                  class: 'unified-action-btn',
                  onClick: () => downloadNative(row.fileId, row.originalName),
                },
                { icon: () => h(HardDriveDownload, { size: 14 }) }
              ),
            default: () => '浏览器原生下载 (直写系统硬盘，零网页内存，适合超大文件)',
          },
        ),
        h(
          NTooltip,
          { trigger: 'hover' },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'small',
                  circle: true,
                  secondary: true,
                  type: 'primary',
                  class: 'unified-action-btn',
                  onClick: () => {
                    message.info(`已开启应用内普通流式下载：“${row.originalName}”`);
                    downloadFile(row.fileId, row.originalName, row.size, { chunked: false });
                  },
                },
                { icon: () => h(Download, { size: 14 }) },
              ),
            default: () => '应用内普通流式下载 (带页面实时进度条与测速)',
          },
        ),
        h(
          NTooltip,
          { trigger: 'hover' },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'small',
                  circle: true,
                  secondary: true,
                  type: 'info',
                  class: 'unified-action-btn',
                  onClick: async () => {
                    message.info(`已开启多线程并发分片下载：“${row.originalName}”`);
                    try {
                      await downloadFile(row.fileId, row.originalName, row.size, { chunked: true });
                      message.success(`文件 “${row.originalName}” 下载完成并已交付保存`);
                    } catch (err: any) {
                      message.error(err.message || '分片下载失败');
                    }
                  },
                },
                { icon: () => h(Layers, { size: 14 }) },
              ),
            default: () => '多线程并发分片下载 (HTTP Range 206)',
          },
        ),
        h(
          NTooltip,
          { trigger: 'hover' },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'small',
                  circle: true,
                  secondary: true,
                  type: 'default',
                  class: 'unified-action-btn',
                  onClick: async () => {
                    try {
                      const url = await createAccessUrl(row.fileId);
                      await copyToClipboard(url, '临时下载直链已复制至剪贴板（有效时长 300 秒）');
                    } catch (err: any) {
                      const rawMsg = err.message || '';
                      if (
                        rawMsg.includes('Storage does not support signed URLs') ||
                        rawMsg.includes('CAPABILITY_NOT_SUPPORTED') ||
                        rawMsg.includes('501')
                      ) {
                        dialog.info({
                          title: '存储能力说明',
                          content:
                            '当前文件保存在宿主机本地磁盘（local-main）。本地文件系统驱动不支持生成临时签名直链，请直接点击“下载”或“并发分片下载”。如需使用云端临时直链签名能力，可切换为 MinIO、阿里云 OSS 或腾讯云 COS 存储适配器。',
                          positiveText: '知道了',
                        });
                      } else {
                        message.error(rawMsg || '生成访问链接失败');
                      }
                    }
                  },
                },
                { icon: () => h(LinkIcon, { size: 14 }) },
              ),
            default: () => '生成临时访问链接 (300秒)',
          },
        ),
        h(
          NTooltip,
          { trigger: 'hover' },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'small',
                  circle: true,
                  secondary: true,
                  type: 'error',
                  class: 'unified-action-btn',
                  onClick: () => {
                    dialog.warning({
                      title: '确认删除文件引用',
                      content: `确定要彻底删除文件引用 “${row.originalName}” 吗？删除后该文件将无法再被当前业务方读取或下载。`,
                      positiveText: '确认删除',
                      negativeText: '取消',
                      onPositiveClick: async () => {
                        try {
                          await deleteFile(row.fileId);
                          message.success(`文件 “${row.originalName}” 引用已成功删除`);
                          loadTableData();
                        } catch (err: any) {
                          message.error(err.message || '删除文件失败');
                        }
                      },
                    });
                  },
                },
                { icon: () => h(Trash2, { size: 14 }) },
              ),
            default: () => '删除文件引用',
          },
        ),
      ]);
    },
  },
];

onMounted(() => {
  loadTableData();
});
</script>

<template>
  <div class="light-console-viewport">
    <!-- 顶部纯白导航栏 -->
    <header class="console-top-navbar">
      <div class="navbar-inner">
        <div class="brand-side">
          <div class="brand-logo-wrap">
            <HardDrive class="brand-logo" :size="20" />
          </div>
          <div class="brand-text-wrap">
            <span class="brand-title">FileBridge 存储管理控制台</span>
            <span class="brand-badge badge-blue">Spring Boot 4.1</span>
            <span class="brand-badge badge-gray">Vue 3 + Naive UI</span>
          </div>
        </div>

        <div class="telemetry-side">
          <div class="nav-chip">
            <Server :size="13" class="chip-icon" />
            <span>
              节点：
              <strong>local-main</strong>
            </span>
          </div>
          <div class="nav-chip">
            <ShieldCheck :size="13" class="chip-icon" />
            <span>
              租户：
              <strong>demo-tenant</strong>
            </span>
          </div>
          <div class="nav-chip throughput-chip">
            <span class="live-dot" />
            <span>
              实时吞吐：
              <strong>{{ activeThroughput }}</strong>
            </span>
          </div>
          <NBadge
            :value="unreadCount"
            :max="99"
            :show="unreadCount > 0"
            :type="unreadErrorCount > 0 ? 'error' : 'info'"
          >
            <NButton size="small" secondary class="nav-log-button" @click="logDrawerOpen = true">
              <template #icon>
                <Terminal :size="14" />
              </template>
              运行日志
            </NButton>
          </NBadge>
          <NButton
            size="small"
            secondary
            circle
            class="nav-refresh-nbtn"
            title="刷新页面数据"
            @click="loadTableData"
          >
            <template #icon>
              <RefreshCw :size="14" :class="{ 'spin-active': isTableLoading }" />
            </template>
          </NButton>
        </div>
      </div>
    </header>

    <main class="console-main-container">
      <!-- 统计指标四卡片 -->
      <section class="stat-metrics-deck">
        <div class="metric-card">
          <div class="metric-icon-box bg-blue-tint">
            <FileText :size="20" class="text-blue" />
          </div>
          <div class="metric-info">
            <div class="metric-label">文件资产总数</div>
            <div class="metric-number">
              {{ filePage.total }}
              <span class="metric-unit">项</span>
            </div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon-box bg-cyan-tint">
            <HardDrive :size="20" class="text-cyan" />
          </div>
          <div class="metric-info">
            <div class="metric-label">已载入存储容量</div>
            <div class="metric-number">{{ formatBytes(totalStorageBytes) }}</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon-box bg-emerald-tint">
            <Activity :size="20" class="text-emerald" />
          </div>
          <div class="metric-info">
            <div class="metric-label">当前运行传输任务</div>
            <div class="metric-number">
              {{ activeUploadCount }}
              <span class="metric-unit">个活动队列</span>
            </div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon-box bg-indigo-tint">
            <CheckCircle2 :size="20" class="text-indigo" />
          </div>
          <div class="metric-info">
            <div class="metric-label">存储实例健康度</div>
            <div class="metric-number">
              NORMAL
              <span class="metric-unit">(在线可用)</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 面板一：上传与传输调度中枢 -->
      <section class="console-card-panel">
        <div class="panel-header">
          <div class="panel-title-group">
            <div class="panel-icon-circle">
              <UploadCloud :size="18" class="text-blue" />
            </div>
            <div class="panel-heading-text">
              <h2 class="panel-main-title">快速上传与传输调度中枢</h2>
              <p class="panel-sub-title">
                双模分流机制：≤10MB 极速直传，>10MB 自动启用多线程 Web Worker 切片与秒传探针
              </p>
            </div>
          </div>
        </div>

        <!-- 拖拽上传板 -->
        <div
          class="light-dropzone"
          :class="{ active: isDragActive }"
          @dragover.prevent="isDragActive = true"
          @dragleave.prevent="isDragActive = false"
          @drop.prevent="onDrop"
          @click="fileInputRef?.click()"
        >
          <input
            ref="fileInputRef"
            type="file"
            multiple
            style="display: none"
            @change="onSelectFiles"
          />
          <div class="dropzone-inner-icon">
            <UploadCloud :size="42" class="text-blue" />
          </div>
          <div class="dropzone-main-text">点击选择文件 或 将大文件拖拽至此处</div>
          <div class="dropzone-hint-text">
            支持任意格式文件批量加入传输管道，增量哈希计算不阻塞页面渲染
          </div>
        </div>

        <!-- 活跃上传任务流 -->
        <div v-if="fileQueue.length > 0" class="active-task-list">
          <div v-for="item in fileQueue" :key="item.id" class="task-item-card">
            <div class="task-top-row">
              <div class="task-file-box">
                <div class="file-icon-badge badge-sm">
                  <FileText :size="13" class="badge-file-icon" />
                </div>
                <span class="task-filename">{{ item.name }}</span>
                <span class="task-filesize">{{ formatBytes(item.size) }}</span>
              </div>

              <div class="task-status-actions">
                <NTooltip trigger="hover">
                  <template #trigger>
                    <div
                      class="status-pill"
                      :class="item.status === 'ERROR' && translateError(item).isWarning ? 'paused' : item.status.toLowerCase()"
                    >
                      <Zap v-if="item.status === 'INSTANT_HIT'" :size="12" />
                      <CheckCircle2 v-else-if="item.status === 'SUCCESS'" :size="12" />
                      <Pause v-else-if="item.status === 'PAUSED'" :size="12" />
                      <AlertTriangle v-else-if="item.status === 'ERROR'" :size="12" />
                      <RefreshCw v-else :size="12" class="spin-active" />
                      <span>{{ statusText(item) }}</span>
                    </div>
                  </template>
                  <span>{{ item.status === 'ERROR' ? translateError(item).detail : statusText(item) }}</span>
                </NTooltip>

                <NButton
                  v-if="item.status === 'UPLOADING'"
                  size="tiny"
                  circle
                  secondary
                  title="暂停上传"
                  @click="pauseUpload(item)"
                >
                  <template #icon><Pause :size="12" /></template>
                </NButton>
                <NButton
                  v-if="item.status === 'PAUSED'"
                  size="tiny"
                  circle
                  secondary
                  type="primary"
                  title="恢复上传"
                  @click="resumeUpload(item)"
                >
                  <template #icon><Play :size="12" /></template>
                </NButton>
                <NButton
                  v-if="item.status === 'ERROR'"
                  size="tiny"
                  circle
                  secondary
                  type="warning"
                  title="继续断点续传 (从已确认切片继续)"
                  @click="retryUpload(item)"
                >
                  <template #icon><Play :size="12" /></template>
                </NButton>
                <NButton
                  size="tiny"
                  circle
                  secondary
                  type="error"
                  title="取消或清退"
                  @click="cancelUpload(item)"
                >
                  <template #icon><Trash2 :size="12" /></template>
                </NButton>
              </div>
            </div>

            <!-- 上传进度条 -->
            <div class="task-progress-rail">
              <div
                class="task-progress-fill"
                :class="{
                  'fill-instant': item.status === 'INSTANT_HIT',
                  'fill-success': item.status === 'SUCCESS',
                }"
                :style="{ width: item.progress + '%' }"
              />
            </div>

            <!-- 大文件分片雷达矩阵 -->
            <div v-if="item.totalParts > 1" class="task-radar-panel">
              <div class="radar-header-line">
                <span>
                  切片雷达 (已确认 {{ item.completedParts ? item.completedParts.size : 0 }} / 共计
                  {{ item.totalParts }} 片)
                </span>
                <span>单片规格：8 MB</span>
              </div>
              <div class="radar-dots-container">
                <div
                  v-for="p in item.totalParts"
                  :key="'part-' + p"
                  class="radar-dot-cell"
                  :class="{
                    'dot-done': item.completedParts && item.completedParts.has(p),
                    'dot-pending':
                      !(item.completedParts && item.completedParts.has(p)) &&
                      item.status === 'UPLOADING',
                  }"
                  :title="'分片 #' + p"
                />
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 面板二：文件资产管理数据表格 -->
      <section class="console-card-panel">
        <!-- 工具栏：完美左右对齐 -->
        <div class="panel-toolbar-row">
          <div class="toolbar-left-group">
            <NInput
              v-model:value="searchKeyword"
              placeholder="按文件名搜索..."
              clearable
              class="toolbar-search-input"
              @keydown.enter="handleSearch"
            >
              <template #prefix>
                <Search :size="14" style="color: #94a3b8" />
              </template>
            </NInput>
            <NButton type="primary" @click="handleSearch">搜索</NButton>
            <NButton secondary @click="handleResetSearch">重置</NButton>
          </div>

          <div class="toolbar-right-group">
            <span class="toolbar-total-label">
              共
              <strong>{{ filePage.total }}</strong>
              项资产
            </span>
            <NButton secondary size="medium" @click="loadTableData">
              <template #icon>
                <RefreshCw :size="13" />
              </template>
              刷新列表
            </NButton>
          </div>
        </div>

        <!-- 活动并发分片下载浮层监视器 -->
        <div v-if="activeDownloads.length > 0" class="active-download-panel">
          <div
            v-for="dl in activeDownloads"
            :key="'dl-card-' + dl.fileId"
            class="download-card-item"
          >
            <div class="download-card-header">
              <div class="download-title-text">
                <Download v-if="dl.mode === 'stream'" :size="15" class="text-blue" />
                <Layers v-else :size="15" class="text-cyan" />
                <span>
                  {{
                    dl.mode === 'stream' ? '正在进行普通流式下载：' : '正在进行多线程并发分片下载：'
                  }}
                  <strong>{{ dl.name }}</strong>
                </span>
                <span class="download-speed-tag">({{ dl.speed }})</span>
              </div>
              <span class="download-percent-tag">{{ dl.progress }}%</span>
            </div>

            <div class="task-progress-rail">
              <div
                class="task-progress-fill"
                :class="{ 'fill-cyan': dl.mode === 'chunked' }"
                :style="{ width: dl.progress + '%' }"
              />
            </div>

            <div
              v-if="dl.mode === 'chunked' && dl.totalParts > 1"
              class="task-radar-panel"
              style="margin-top: 10px"
            >
              <div class="radar-header-line">
                <span>
                  下载切片雷达 (已抓取 {{ dl.completedParts ? dl.completedParts.size : 0 }} / 共计
                  {{ dl.totalParts }} 片)
                </span>
                <span>HTTP Range 206 协议通道</span>
              </div>
              <div class="radar-dots-container">
                <div
                  v-for="p in dl.totalParts"
                  :key="'dl-part-' + p"
                  class="radar-dot-cell"
                  :class="{
                    'dot-download-done': dl.completedParts && dl.completedParts.has(p),
                    'dot-download-active': !(dl.completedParts && dl.completedParts.has(p)),
                  }"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- 资产数据表格 -->
        <div class="table-wrap-box">
          <NDataTable
            :columns="columns"
            :data="filePage.items"
            :loading="isTableLoading"
            :bordered="false"
            :single-line="false"
            class="light-naive-table"
          />
        </div>

        <!-- 分页器：右对齐与外边距统一 -->
        <div class="panel-pagination-row">
          <NPagination
            v-model:page="currentPage"
            v-model:page-size="pageSize"
            :page-count="Math.ceil(filePage.total / pageSize) || 1"
            :page-sizes="[10, 20, 50]"
            show-size-picker
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </section>
    </main>

    <NDrawer v-model:show="logDrawerOpen" placement="right" width="min(520px, 100vw)">
      <NDrawerContent title="运行日志" closable class="operation-log-drawer">
        <template #header>
          <div class="log-drawer-title">
            <div class="log-drawer-title-icon">
              <Terminal :size="17" />
            </div>
            <div>
              <div class="log-drawer-heading">运行日志</div>
              <div class="log-drawer-caption">仅保留当前页面最近 300 条客户端操作记录</div>
            </div>
          </div>
        </template>

        <div class="log-toolbar">
          <NSelect
            v-model:value="logLevelFilter"
            :options="logLevelOptions"
            size="small"
            class="log-level-select"
          />
          <div class="log-toolbar-actions">
            <NButton
              size="small"
              secondary
              :disabled="filteredLogs.length === 0"
              @click="copyVisibleLogs"
            >
              <template #icon><Copy :size="13" /></template>
              复制
            </NButton>
            <NButton
              size="small"
              secondary
              type="error"
              :disabled="operationLogs.length === 0"
              @click="clearLogs"
            >
              <template #icon><Trash2 :size="13" /></template>
              清空
            </NButton>
          </div>
        </div>

        <div class="log-summary-row">
          <span>当前显示 {{ filteredLogs.length }} 条</span>
          <span>最新记录在前</span>
        </div>

        <NEmpty
          v-if="filteredLogs.length === 0"
          description="暂无符合条件的运行日志"
          class="log-empty-state"
        />

        <div v-else class="operation-log-list">
          <article
            v-for="entry in filteredLogs"
            :key="entry.id"
            class="operation-log-item"
            :class="`log-level-${entry.level.toLowerCase()}`"
          >
            <div class="log-item-header">
              <div class="log-item-identity">
                <component :is="levelIcon(entry.level)" :size="14" class="log-level-icon" />
                <span class="log-level-label">{{ entry.level }}</span>
                <span class="log-category-label">{{ entry.category }}</span>
              </div>
              <time class="log-time-label">{{ formatLogTime(entry.timestamp) }}</time>
            </div>
            <div class="log-message">{{ entry.message }}</div>
            <div v-if="logContextFields(entry).length > 0" class="log-context-list">
              <code v-for="field in logContextFields(entry)" :key="field">{{ field }}</code>
            </div>
          </article>
        </div>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
/* 整个视口：清爽蓝白灰底 */
.light-console-viewport {
  min-height: 100vh;
  background-color: #f8fafc;
  color: #0f172a;
}

/* 顶部纯白导航 */
.console-top-navbar {
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  position: sticky;
  top: 0;
  z-index: 50;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
}

.navbar-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: 12px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.brand-side {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-logo {
  color: #2563eb;
}

.brand-text-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.brand-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 500;
}

.badge-blue {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #dbeafe;
}

.badge-gray {
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}

.telemetry-side {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 5px 12px;
  border-radius: 8px;
}

.nav-chip strong {
  color: #0f172a;
}

.chip-icon {
  color: #64748b;
}

.throughput-chip {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
  font-family: ui-monospace, SFMono-Regular, monospace;
}

.throughput-chip strong {
  color: #15803d;
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: #16a34a;
  box-shadow: 0 0 6px #16a34a;
}

.nav-refresh-nbtn {
  color: #64748b;
}

.nav-log-button {
  color: #334155;
}

.log-drawer-title {
  display: flex;
  align-items: center;
  gap: 11px;
}

.log-drawer-title-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.log-drawer-heading {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
}

.log-drawer-caption {
  margin-top: 2px;
  color: #64748b;
  font-size: 11px;
  font-weight: 400;
}

.log-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e2e8f0;
}

.log-level-select {
  width: 138px;
}

.log-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-summary-row {
  display: flex;
  justify-content: space-between;
  padding: 11px 1px;
  color: #94a3b8;
  font-size: 11px;
}

.log-empty-state {
  margin-top: 72px;
}

.operation-log-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding-bottom: 20px;
}

.operation-log-item {
  border: 1px solid #e2e8f0;
  border-left-width: 3px;
  border-radius: 9px;
  padding: 11px 12px;
  background: #f8fafc;
}

.operation-log-item.log-level-debug {
  border-left-color: #94a3b8;
}

.operation-log-item.log-level-info {
  border-left-color: #2563eb;
}

.operation-log-item.log-level-warn {
  border-left-color: #d97706;
  background: #fffbeb;
}

.operation-log-item.log-level-error {
  border-left-color: #dc2626;
  background: #fef2f2;
}

.log-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.log-item-identity {
  display: flex;
  align-items: center;
  gap: 6px;
}

.log-level-icon {
  color: #64748b;
}

.log-level-error .log-level-icon {
  color: #dc2626;
}

.log-level-warn .log-level-icon {
  color: #d97706;
}

.log-level-info .log-level-icon {
  color: #2563eb;
}

.log-level-label,
.log-category-label,
.log-time-label,
.log-context-list code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.log-level-label {
  font-size: 10px;
  font-weight: 700;
  color: #334155;
}

.log-category-label {
  padding: 1px 6px;
  border-radius: 4px;
  color: #475569;
  background: #e2e8f0;
  font-size: 9px;
  font-weight: 600;
}

.log-time-label {
  flex-shrink: 0;
  color: #94a3b8;
  font-size: 10px;
}

.log-message {
  margin-top: 8px;
  color: #1e293b;
  font-size: 12px;
  line-height: 1.55;
  word-break: break-word;
}

.log-context-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}

.log-context-list code {
  max-width: 100%;
  overflow: hidden;
  padding: 2px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  color: #475569;
  background: rgba(255, 255, 255, 0.78);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 主体容器 */
.console-main-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 32px 64px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 四张统计卡片 */
.stat-metrics-deck {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}

.metric-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.04);
}

.metric-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bg-blue-tint {
  background: #eff6ff;
}
.text-blue {
  color: #2563eb;
}

.bg-cyan-tint {
  background: #ecfeff;
}
.text-cyan {
  color: #0891b2;
}

.bg-emerald-tint {
  background: #f0fdf4;
}
.text-emerald {
  color: #16a34a;
}

.bg-indigo-tint {
  background: #eef2ff;
}
.text-indigo {
  color: #4f46e5;
}

.metric-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.metric-number {
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

.metric-unit {
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
}

/* 白底卡片面板 */
.console-card-panel {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 24px;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.04);
}

.panel-header {
  margin-bottom: 18px;
}

.panel-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel-icon-circle {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-main-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.panel-sub-title {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

/* 拖拽区域 */
.light-dropzone {
  border: 2px dashed #cbd5e1;
  background: #f8fafc;
  border-radius: 12px;
  padding: 38px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.light-dropzone:hover,
.light-dropzone.active {
  border-color: #2563eb;
  background: #eff6ff;
  transform: translateY(-1px);
}

.dropzone-inner-icon {
  margin-bottom: 10px;
}

.dropzone-main-text {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.dropzone-hint-text {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

/* 任务列表 */
.active-task-list {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-item-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 18px;
}

.task-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-file-box {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-filename {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.task-filesize {
  font-size: 11px;
  font-family: ui-monospace, monospace;
  color: #64748b;
}

.task-status-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
}

.status-pill.instant_hit {
  background: #ecfeff;
  color: #0891b2;
  border: 1px solid #a5f3fc;
}

.status-pill.success {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.status-pill.uploading,
.status-pill.hashing,
.status-pill.completing {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
}

.status-pill.paused {
  background: #fffbeb;
  color: #d97706;
  border: 1px solid #fde68a;
}

.status-pill.error {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.task-progress-rail {
  width: 100%;
  height: 5px;
  background: #e2e8f0;
  border-radius: 999px;
  margin-top: 12px;
  overflow: hidden;
}

.task-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #60a5fa);
  transition: width 0.2s ease-out;
}

.fill-instant {
  background: linear-gradient(90deg, #0891b2, #06b6d4);
}

.fill-success {
  background: linear-gradient(90deg, #16a34a, #22c55e);
}

.fill-cyan {
  background: linear-gradient(90deg, #0891b2, #3b82f6);
}

.task-radar-panel {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
}

.radar-header-line {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 6px;
}

.radar-dots-container {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(10px, 1fr));
  gap: 3px;
  max-height: 100px;
  overflow-y: auto;
}

.radar-dot-cell {
  height: 6px;
  border-radius: 2px;
  background: #e2e8f0;
}

.dot-done {
  background: #2563eb;
}

.dot-pending {
  background: #93c5fd;
}

.dot-download-done {
  background: #0891b2;
}

.dot-download-active {
  background: #a5f3fc;
}

/* 工具栏排版 */
.panel-toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.toolbar-left-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-search-input {
  width: 280px;
}

.toolbar-right-group {
  display: flex;
  align-items: center;
  gap: 14px;
}

.toolbar-total-label {
  font-size: 13px;
  color: #64748b;
}

.toolbar-total-label strong {
  color: #0f172a;
}

/* 下载监视浮层 */
.active-download-panel {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.download-card-item {
  background: #ecfeff;
  border: 1px solid #a5f3fc;
  border-radius: 10px;
  padding: 14px 18px;
}

.download-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #0891b2;
}

.download-title-text {
  display: flex;
  align-items: center;
  gap: 8px;
}

.download-speed-tag {
  font-family: ui-monospace, monospace;
  font-size: 11px;
  color: #0284c7;
}

.download-percent-tag {
  font-weight: 700;
  font-family: ui-monospace, monospace;
}

/* 表格容器与单元格对齐 */
.table-wrap-box {
  width: 100%;
  overflow-x: auto;
}

/* 文件名单元格：严格光学居中对齐 */
:deep(.file-row-flex) {
  display: flex;
  align-items: center;
  gap: 10px;
  line-height: 1;
  min-height: 32px;
}

:deep(.file-icon-badge) {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

:deep(.badge-sm) {
  width: 24px;
  height: 24px;
}

:deep(.badge-file-icon) {
  color: #2563eb;
  display: block;
}

:deep(.file-name-label) {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.table-mono-text) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: #475569;
  line-height: 1;
}

:deep(.table-date-text) {
  font-size: 12px;
  color: #64748b;
  line-height: 1;
}

:deep(.table-hash-wrap) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  line-height: 1;
}

:deep(.hash-copy-btn) {
  color: #94a3b8;
}

:deep(.hash-copy-btn:hover) {
  color: #2563eb;
}

:deep(.custom-tag-blue) {
  background: #eff6ff !important;
  color: #1d4ed8 !important;
  border: 1px solid #bfdbfe !important;
}

:deep(.custom-tag-green) {
  background: #f0fdf4 !important;
  color: #15803d !important;
  border: 1px solid #bbf7d0 !important;
}

/* 操作列统一按钮样式 */
:deep(.unified-action-btn) {
  width: 28px !important;
  height: 28px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
}

:deep(.n-data-table-td) {
  vertical-align: middle;
}

/* 分页行对齐 */
.panel-pagination-row {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.spin-active {
  animation: spin-kf 1.2s linear infinite;
}

@keyframes spin-kf {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
