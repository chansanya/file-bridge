import { Sha256 } from './sha256';

self.onmessage = async (event: MessageEvent<{ file: File; blockSize: number }>) => {
  const { file, blockSize } = event.data;
  const hasher = new Sha256();
  let offset = 0;
  let lastReport = performance.now();

  while (offset < file.size) {
    const chunk = file.slice(offset, Math.min(file.size, offset + blockSize));
    hasher.update(await chunk.arrayBuffer());
    offset += chunk.size;
    const now = performance.now();
    if (now - lastReport > 80 || offset >= file.size) {
      self.postMessage({ type: 'PROGRESS', progress: offset / file.size });
      lastReport = now;
    }
  }

  self.postMessage({ type: 'DONE', hash: hasher.digest() });
};
