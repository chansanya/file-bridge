import { ref } from 'vue';
import { Sha256 } from './sha256.js';

const WORKER_SHA256_SCRIPT = `
class Sha256 {
  constructor() {
    this.h = new Uint32Array([
      0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
    ]);
    this.buffer = new Uint8Array(64);
    this.bufferLength = 0;
    this.bytes = 0;
  }
  update(data) {
    const input = data instanceof Uint8Array ? data : new Uint8Array(data);
    this.bytes += input.length;
    let offset = 0;
    while (offset < input.length) {
      const take = Math.min(64 - this.bufferLength, input.length - offset);
      this.buffer.set(input.subarray(offset, offset + take), this.bufferLength);
      this.bufferLength += take;
      offset += take;
      if (this.bufferLength === 64) {
        this.compress(this.buffer);
        this.bufferLength = 0;
      }
    }
    return this;
  }
  digest() {
    const bits = this.bytes * 8;
    this.buffer[this.bufferLength++] = 0x80;
    if (this.bufferLength > 56) {
      this.buffer.fill(0, this.bufferLength);
      this.compress(this.buffer);
      this.bufferLength = 0;
    }
    this.buffer.fill(0, this.bufferLength, 56);
    const hi = Math.floor(bits / 0x100000000), lo = bits >>> 0;
    new DataView(this.buffer.buffer).setUint32(56, hi);
    new DataView(this.buffer.buffer).setUint32(60, lo);
    this.compress(this.buffer);
    return Array.from(this.h, (v) => v.toString(16).padStart(8, '0')).join('');
  }
  compress(chunk) {
    const k = K, w = new Uint32Array(64), v = new DataView(chunk.buffer, chunk.byteOffset, 64);
    for (let i = 0; i < 16; i++) w[i] = v.getUint32(i * 4);
    for (let i = 16; i < 64; i++) {
      const a = w[i - 15], b = w[i - 2];
      const s0 = (a >>> 7 | a << 25) ^ (a >>> 18 | a << 14) ^ (a >>> 3);
      const s1 = (b >>> 17 | b << 15) ^ (b >>> 19 | b << 13) ^ (b >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, h] = this.h;
    for (let i = 0; i < 64; i++) {
      const s1 = (e >>> 6 | e << 26) ^ (e >>> 11 | e << 21) ^ (e >>> 25 | e << 7);
      const ch = (e & f) ^ (~e & g);
      const t1 = (h + s1 + ch + k[i] + w[i]) >>> 0;
      const s0 = (a >>> 2 | a << 30) ^ (a >>> 13 | a << 19) ^ (a >>> 22 | a << 10);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const t2 = (s0 + maj) >>> 0;
      h = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
    }
    this.h[0] = (this.h[0] + a) >>> 0;
    this.h[1] = (this.h[1] + b) >>> 0;
    this.h[2] = (this.h[2] + c) >>> 0;
    this.h[3] = (this.h[3] + d) >>> 0;
    this.h[4] = (this.h[4] + e) >>> 0;
    this.h[5] = (this.h[5] + f) >>> 0;
    this.h[6] = (this.h[6] + g) >>> 0;
    this.h[7] = (this.h[7] + h) >>> 0;
  }
}
const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
]);

self.onmessage = async (e) => {
  const { file, blockSize } = e.data;
  const hasher = new Sha256();
  let offset = 0;
  let lastReport = performance.now();
  while (offset < file.size) {
    const chunk = file.slice(offset, Math.min(file.size, offset + blockSize));
    const buf = await chunk.arrayBuffer();
    hasher.update(buf);
    offset += chunk.size;
    const now = performance.now();
    if (now - lastReport > 80 || offset >= file.size) {
      self.postMessage({ type: 'PROGRESS', progress: offset / file.size });
      lastReport = now;
    }
  }
  self.postMessage({ type: 'DONE', hash: hasher.digest() });
};
`;

export function useFileBridge(options = {}) {
  const apiBase = options.apiBase || '/api/file-bridge';
  const chunkThreshold = options.chunkThreshold || 10 * 1024 * 1024;
  const preferredPartSize = options.preferredPartSize || 8 * 1024 * 1024;
  const hashBlockSize = options.hashBlockSize || 4 * 1024 * 1024;
  const maxConcurrency = options.concurrency || 3;

  const fileQueue = ref([]);
  const isProcessing = ref(false);

  async function fallbackComputeHash(item) {
    const hasher = new Sha256();
    let offset = 0;
    while (offset < item.size) {
      if (item.aborted) throw new Error('ABORTED');
      const slice = item.rawFile.slice(offset, Math.min(item.size, offset + hashBlockSize));
      const buffer = await slice.arrayBuffer();
      hasher.update(buffer);
      offset += slice.size;
      item.progress = Math.min(15, Math.round((offset / item.size) * 15));
      await new Promise((r) => requestAnimationFrame(r));
    }
    return hasher.digest();
  }

  function computeHashWithWorker(item) {
    if (typeof Worker === 'undefined') {
      return fallbackComputeHash(item);
    }

    return new Promise((resolve, reject) => {
      let worker;
      let workerUrl;

      const cleanup = () => {
        if (worker) {
          worker.terminate();
          item.worker = null;
          worker = null;
        }
        if (workerUrl) {
          URL.revokeObjectURL(workerUrl);
          workerUrl = null;
        }
      };

      try {
        const blob = new Blob([WORKER_SHA256_SCRIPT], { type: 'application/javascript' });
        workerUrl = URL.createObjectURL(blob);
        worker = new Worker(workerUrl);
        item.worker = worker;

        worker.onmessage = (e) => {
          if (e.data.type === 'PROGRESS') {
            item.progress = Math.min(15, Math.round(e.data.progress * 15));
          } else if (e.data.type === 'DONE') {
            cleanup();
            resolve(e.data.hash);
          }
        };

        worker.onerror = (err) => {
          cleanup();
          fallbackComputeHash(item).then(resolve).catch(reject);
        };

        worker.postMessage({ file: item.rawFile, blockSize: hashBlockSize });
      } catch (err) {
        cleanup();
        fallbackComputeHash(item).then(resolve).catch(reject);
      }
    });
  }

  async function uploadDirect(item) {
    const formData = new FormData();
    formData.append('file', item.rawFile);
    if (item.businessType) formData.append('businessType', item.businessType);
    if (item.businessId) formData.append('businessId', item.businessId);

    const xhr = new XMLHttpRequest();
    item.xhr = xhr;

    await new Promise((resolve, reject) => {
      xhr.open('POST', `${apiBase}/files`);
      xhr.setRequestHeader('Idempotency-Key', crypto.randomUUID());

      let lastLoaded = 0;
      let lastTime = performance.now();

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const now = performance.now();
          if (now - lastTime > 300) {
            const bytesPerSec = ((e.loaded - lastLoaded) / (now - lastTime)) * 1000;
            item.speed = `${(bytesPerSec / (1024 * 1024)).toFixed(1)} MB/s`;
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
            resolve(data);
          } catch (err) {
            reject(new Error('Invalid server JSON response'));
          }
        } else {
          reject(new Error(`HTTP ${xhr.status}: ${xhr.responseText}`));
        }
      };

      xhr.onerror = () => reject(new Error('Network transmission error'));
      xhr.onabort = () => reject(new Error('Transmission aborted'));

      xhr.send(formData);
    });
  }

  async function uploadMultipart(item) {
    const resumeKey = `fb:${item.size}:${item.sha256}`;
    item.resumeKey = resumeKey;
    item.resumable = true;
    let uploadId = localStorage.getItem(resumeKey);
    let partSize = preferredPartSize;
    let totalParts = Math.ceil(item.size / preferredPartSize);

    if (uploadId) {
      try {
        const queryRes = await fetch(`${apiBase}/uploads/${uploadId}`);
        if (queryRes.ok) {
          const detail = await queryRes.json();
          partSize = detail.partSize;
          totalParts = detail.totalParts;
          item.completedParts = new Set(detail.completedParts || []);
        } else {
          uploadId = null;
          localStorage.removeItem(resumeKey);
        }
      } catch {
        uploadId = null;
      }
    }

    if (!uploadId) {
      const initRes = await fetch(`${apiBase}/uploads`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          originalName: item.name,
          size: item.size,
          sha256: item.sha256,
          contentType: item.rawFile.type || 'application/octet-stream',
          businessType: item.businessType,
          businessId: item.businessId,
        }),
      });

      if (!initRes.ok) throw new Error(`Init Failed: ${await initRes.text()}`);
      const initData = await initRes.json();

      if (initData.mode === 'INSTANT') {
        item.fileId = initData.fileId;
        item.progress = 100;
        item.status = 'INSTANT_HIT';
        return;
      }

      uploadId = initData.uploadId;
      partSize = initData.partSize;
      totalParts = initData.totalParts;
      localStorage.setItem(resumeKey, uploadId);
    }

    item.uploadId = uploadId;
    item.partSize = partSize;
    item.totalParts = totalParts;
    item.status = 'UPLOADING';

    const pendingParts = [];
    for (let p = 1; p <= totalParts; p++) {
      if (!item.completedParts.has(p)) pendingParts.push(p);
    }

    let loadedBytes = item.completedParts.size * partSize;
    let lastTime = performance.now();
    let lastBytes = loadedBytes;

    const worker = async () => {
      while (pendingParts.length > 0) {
        if (item.aborted) throw new Error('ABORTED');
        while (item.paused) {
          await new Promise((resolve) => setTimeout(resolve, 300));
          if (item.aborted) throw new Error('ABORTED');
        }

        const partNumber = pendingParts.shift();
        if (!partNumber) break;

        const start = (partNumber - 1) * partSize;
        const end = Math.min(item.size, start + partSize);
        const chunkBlob = item.rawFile.slice(start, end);

        const putRes = await fetch(`${apiBase}/uploads/${item.uploadId}/parts/${partNumber}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/octet-stream' },
          body: chunkBlob,
        });

        if (!putRes.ok) {
          pendingParts.unshift(partNumber);
          throw await toApiError(putRes, `Part ${partNumber} failed`);
        }

        item.completedParts.add(partNumber);
        loadedBytes += chunkBlob.size;

        const now = performance.now();
        if (now - lastTime > 300) {
          const speedBps = ((loadedBytes - lastBytes) / (now - lastTime)) * 1000;
          item.speed = `${(speedBps / (1024 * 1024)).toFixed(1)} MB/s`;
          lastTime = now;
          lastBytes = loadedBytes;
        }

        item.progress = 15 + Math.round((item.completedParts.size / item.totalParts) * 80);
      }
    };

    const workerCount = Math.min(maxConcurrency, pendingParts.length || 1);
    await Promise.all(Array.from({ length: workerCount }, worker));

    item.status = 'COMPLETING';
    const compRes = await fetch(`${apiBase}/uploads/${item.uploadId}/complete`, { method: 'POST' });
    if (!compRes.ok) throw new Error(`Complete failed: ${await compRes.text()}`);

    let compData = await compRes.json();
    while (compData.status === 'COMPLETING' || compData.status === 'VERIFYING') {
      await new Promise((r) => setTimeout(r, 1200));
      if (item.aborted) throw new Error('ABORTED');
      const pollRes = await fetch(`${apiBase}/uploads/${item.uploadId}`);
      if (!pollRes.ok) throw await toApiError(pollRes, 'Upload status query failed');
      compData = await pollRes.json();
    }

    if (compData.status !== 'COMPLETED' || !compData.fileId) {
      throw new Error(`Upload ended with unexpected state: ${compData.status}`);
    }

    localStorage.removeItem(resumeKey);
    item.fileId = compData.fileId;
    item.progress = 100;
    item.status = 'SUCCESS';
  }

  async function processItem(item) {
    try {
      if (item.size <= chunkThreshold) {
        item.status = 'UPLOADING';
        await uploadDirect(item);
      } else {
        item.status = 'HASHING';
        item.sha256 = await computeHashWithWorker(item);
        await uploadMultipart(item);
      }
    } catch (err) {
      if (item.aborted) {
        item.status = 'CANCELLED';
      } else {
        item.status = 'ERROR';
        item.errorMessage = err.message || 'Transmission failed';
      }
    }
  }

  async function processQueue() {
    if (isProcessing.value) return;
    isProcessing.value = true;
    for (const item of fileQueue.value) {
      if (item.status === 'IDLE') {
        await processItem(item);
      }
    }
    isProcessing.value = false;
  }

  function addFiles(files, context = {}) {
    const list = Array.from(files);
    for (const file of list) {
      fileQueue.value.unshift({
        id: crypto.randomUUID(),
        rawFile: file,
        name: file.name,
        size: file.size,
        businessType: context.businessType,
        businessId: context.businessId,
        status: 'IDLE',
        progress: 0,
        speed: '0.0 MB/s',
        totalParts: Math.ceil(file.size / preferredPartSize),
        completedParts: new Set(),
        paused: false,
        aborted: false,
        worker: null,
        fileId: null,
        uploadId: null,
        errorMessage: null,
        resumable: false,
        resumeKey: null,
      });
    }
    processQueue();
  }

  function pauseItem(item) {
    if (item.status === 'UPLOADING' && item.resumable) {
      item.paused = true;
      item.status = 'PAUSED';
    }
  }

  function resumeItem(item) {
    if (item.status === 'PAUSED') {
      item.paused = false;
      item.status = 'UPLOADING';
      if (!isProcessing.value) processQueue();
    }
  }

  async function cancelItem(item) {
    item.aborted = true;
    if (item.worker) {
      item.worker.terminate();
      item.worker = null;
    }
    if (item.xhr) {
      item.xhr.abort();
    }
    if (item.resumeKey) localStorage.removeItem(item.resumeKey);
    if (item.uploadId) {
      try {
        await fetch(`${apiBase}/uploads/${item.uploadId}`, { method: 'DELETE' });
      } catch {}
    }
    item.status = 'CANCELLED';
    const index = fileQueue.value.indexOf(item);
    if (index > -1) fileQueue.value.splice(index, 1);
  }

  async function toApiError(response, fallbackMessage) {
    let message = fallbackMessage;
    try {
      const body = await response.json();
      message = body.message || body.code || message;
    } catch {
      const text = await response.text();
      if (text) message = text;
    }
    return new Error(`${response.status}: ${message}`);
  }

  async function deleteFile(fileId) {
    const res = await fetch(`${apiBase}/files/${fileId}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
  }

  function getDownloadUrl(fileId) {
    return `${apiBase}/files/${fileId}/download`;
  }

  return {
    fileQueue,
    isProcessing,
    addFiles,
    pauseItem,
    resumeItem,
    cancelItem,
    deleteFile,
    getDownloadUrl,
  };
}
