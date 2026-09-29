import { ref } from 'vue';
import { Sha256 } from './sha256.js';

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
    if (typeof Worker === 'undefined') return fallbackComputeHash(item);

    return new Promise((resolve, reject) => {
      let worker;
      const cleanup = () => {
        if (worker) worker.terminate();
        item.worker = null;
      };
      try {
        worker = new Worker(new URL('./hash-worker.js', import.meta.url), { type: 'module' });
        item.worker = worker;
        worker.onmessage = (event) => {
          if (event.data.type === 'PROGRESS') {
            item.progress = Math.min(15, Math.round(event.data.progress * 15));
          } else if (event.data.type === 'DONE') {
            cleanup();
            resolve(event.data.hash);
          }
        };
        worker.onerror = () => {
          cleanup();
          fallbackComputeHash(item).then(resolve).catch(reject);
        };
        worker.postMessage({ file: item.rawFile, blockSize: hashBlockSize });
      } catch (error) {
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
    try {
      while (true) {
        const next = fileQueue.value.find((item) => item.status === 'IDLE');
        if (!next) break;
        await processItem(next);
      }
    } finally {
      isProcessing.value = false;
    }
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
    const text = await response.text();
    let message = text || fallbackMessage;
    try {
      const body = JSON.parse(text);
      message = body.message || body.code || message;
    } catch {}
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
