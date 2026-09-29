import { ref } from 'vue';

export type OperationLogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
export type OperationLogCategory = 'SYSTEM' | 'HTTP' | 'UPLOAD' | 'DOWNLOAD' | 'FILE';

export interface OperationLogContext {
  requestId?: string;
  fileId?: string;
  uploadId?: string;
  partNumber?: number;
  progress?: number;
  httpStatus?: number;
  durationMs?: number;
  bytes?: number;
}

export interface OperationLogEntry extends OperationLogContext {
  id: string;
  timestamp: number;
  level: OperationLogLevel;
  category: OperationLogCategory;
  message: string;
}

export interface AppendOperationLog extends OperationLogContext {
  level: OperationLogLevel;
  category: OperationLogCategory;
  message: string;
}

const MAX_LOGS = 300;
const entries = ref<OperationLogEntry[]>([]);
const unreadCount = ref(0);
const unreadErrorCount = ref(0);
const viewerOpen = ref(false);

function appendLog(input: AppendOperationLog): OperationLogEntry {
  const entry: OperationLogEntry = {
    id: crypto.randomUUID(),
    timestamp: Date.now(),
    ...input,
  };
  entries.value.unshift(entry);
  if (entries.value.length > MAX_LOGS) entries.value.length = MAX_LOGS;
  if (!viewerOpen.value) {
    unreadCount.value++;
    if (entry.level === 'ERROR') unreadErrorCount.value++;
  }
  return entry;
}

function clearLogs(): void {
  entries.value = [];
  unreadCount.value = 0;
  unreadErrorCount.value = 0;
}

function setViewerOpen(open: boolean): void {
  viewerOpen.value = open;
  if (open) {
    unreadCount.value = 0;
    unreadErrorCount.value = 0;
  }
}

function formatLogs(logs: OperationLogEntry[]): string {
  return logs
    .map((entry) => {
      const context = formatContext(entry);
      const time = new Date(entry.timestamp).toISOString();
      return `${time} ${entry.level.padEnd(5)} ${entry.category.padEnd(8)} ${entry.message}${context ? ` ${context}` : ''}`;
    })
    .join('\n');
}

function formatContext(entry: OperationLogEntry): string {
  const values: string[] = [];
  if (entry.requestId) values.push(`requestId=${entry.requestId}`);
  if (entry.fileId) values.push(`fileId=${entry.fileId}`);
  if (entry.uploadId) values.push(`uploadId=${entry.uploadId}`);
  if (entry.partNumber !== undefined) values.push(`part=${entry.partNumber}`);
  if (entry.progress !== undefined) values.push(`progress=${entry.progress}%`);
  if (entry.httpStatus !== undefined) values.push(`status=${entry.httpStatus}`);
  if (entry.durationMs !== undefined) values.push(`durationMs=${entry.durationMs}`);
  if (entry.bytes !== undefined) values.push(`bytes=${entry.bytes}`);
  return values.join(' ');
}

export function useOperationLog() {
  return {
    entries,
    unreadCount,
    unreadErrorCount,
    viewerOpen,
    appendLog,
    clearLogs,
    setViewerOpen,
    formatLogs,
  };
}
