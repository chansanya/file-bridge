import { Sha256 } from './sha256.js';

const API_BASE_PATH = '/api/file-bridge';
const HASH_CHUNK_SIZE = 4 * 1024 * 1024;

const fileInput = document.querySelector('#file');
const progressBar = document.querySelector('#progress');
const resultOutput = document.querySelector('#result');
const stateText = document.querySelector('#state');
const uploadButton = document.querySelector('#upload');
const pauseButton = document.querySelector('#cancel');

let paused = false;

pauseButton.onclick = () => {
  paused = true;
  pauseButton.disabled = true;
  stateText.textContent = '已暂停，可再次点击开始上传恢复';
};

uploadButton.onclick = async () => {
  const selectedFile = fileInput.files[0];
  if (!selectedFile) return;

  paused = false;
  pauseButton.disabled = false;

  try {
    stateText.textContent = '计算文件摘要';
    const sha256 = await calculateSha256(selectedFile);
    const resumeKey = `filebridge:${selectedFile.size}:${sha256}`;
    const upload = await initializeUpload(selectedFile, sha256, resumeKey);

    if (upload.mode === 'INSTANT') {
      progressBar.value = 100;
      resultOutput.textContent = JSON.stringify(upload, null, 2);
      stateText.textContent = '秒传完成';
      return;
    }

    await uploadMissingParts(selectedFile, upload);

    const completed = await requestJson(`${API_BASE_PATH}/uploads/${upload.uploadId}/complete`, {
      method: 'POST',
    });
    progressBar.value = 100;
    resultOutput.textContent = JSON.stringify(completed, null, 2);
    stateText.textContent = '服务端正在合并和校验';
    localStorage.removeItem(resumeKey);
  } catch (error) {
    stateText.textContent = '上传失败';
    resultOutput.textContent = error.message;
  } finally {
    pauseButton.disabled = true;
  }
};

async function calculateSha256(file) {
  const hash = new Sha256();

  for (let offset = 0; offset < file.size; offset += HASH_CHUNK_SIZE) {
    const chunk = file.slice(offset, Math.min(file.size, offset + HASH_CHUNK_SIZE));
    hash.update(await chunk.arrayBuffer());
    progressBar.value = Math.min(10, (offset / file.size) * 10);
    await new Promise(requestAnimationFrame);
  }

  return hash.digest();
}

async function initializeUpload(file, sha256, resumeKey) {
  const savedUploadId = localStorage.getItem(resumeKey);
  if (savedUploadId) {
    const status = await requestJson(`${API_BASE_PATH}/uploads/${savedUploadId}`);
    return {
      mode: 'UPLOAD',
      uploadId: savedUploadId,
      partSize: status.partSize,
      totalParts: status.totalParts,
      completedParts: status.completedParts,
    };
  }

  const upload = await requestJson(`${API_BASE_PATH}/uploads`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': crypto.randomUUID(),
    },
    body: JSON.stringify({
      originalName: file.name,
      size: file.size,
      sha256,
      contentType: file.type || 'application/octet-stream',
    }),
  });

  if (upload.mode === 'UPLOAD') {
    localStorage.setItem(resumeKey, upload.uploadId);
    upload.completedParts = [];
  }

  return upload;
}

async function uploadMissingParts(file, upload) {
  const completedParts = new Set(upload.completedParts || []);

  for (let partNumber = 1; partNumber <= upload.totalParts; partNumber++) {
    while (paused) {
      await new Promise((resolve) => setTimeout(resolve, 300));
    }
    if (completedParts.has(partNumber)) continue;

    const start = (partNumber - 1) * upload.partSize;
    const body = file.slice(start, Math.min(file.size, start + upload.partSize));
    await requestJson(
      `${API_BASE_PATH}/uploads/${upload.uploadId}/parts/${partNumber}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/octet-stream' },
        body,
      },
      true,
    );

    progressBar.value = 10 + (85 * partNumber) / upload.totalParts;
    stateText.textContent = `上传分片 ${partNumber}/${upload.totalParts}`;
  }
}

async function requestJson(url, options = {}, emptyResponse = false) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`${response.status} ${await response.text()}`);
  }
  return emptyResponse ? {} : response.json();
}
