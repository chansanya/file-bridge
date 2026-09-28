import { createApp, ref, computed, h } from 'vue';
import { useFileBridge } from './useFileBridge.js';

const LucideIcons = {
  UploadCloud: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('path', { d: 'M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242' }),
      h('path', { d: 'M12 12v9' }),
      h('path', { d: 'm16 16-4-4-4 4' }),
    ]),
  CheckCircle2: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('circle', { cx: '12', cy: '12', r: '10' }),
      h('path', { d: 'm9 12 2 2 4-4' }),
    ]),
  AlertTriangle: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('path', { d: 'm21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z' }),
      h('line', { x1: '12', y1: '9', x2: '12', y2: '13' }),
      h('line', { x1: '12', y1: '17', x2: '12.01', y2: '17' }),
    ]),
  Zap: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('polygon', { points: '13 2 3 14 12 14 11 22 21 10 12 10 13 2' }),
    ]),
  HardDrive: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('line', { x1: '22', y1: '12', x2: '2', y2: '12' }),
      h('path', {
        d: 'M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z',
      }),
      h('line', { x1: '6', y1: '16', x2: '6.01', y2: '16' }),
      h('line', { x1: '10', y1: '16', x2: '10.01', y2: '16' }),
    ]),
  FileCode2: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('path', { d: 'M4 22h14a2 2 0 0 0 2-2V7.5L14.5 2H6a2 2 0 0 0-2 2v4' }),
      h('polyline', { points: '14 2 14 8 20 8' }),
      h('path', { d: 'm9 18 3-3-3-3' }),
      h('path', { d: 'm5 12-3 3 3 3' }),
    ]),
  RefreshCw: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('path', { d: 'M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8' }),
      h('path', { d: 'M21 3v5h-5' }),
      h('path', { d: 'M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16' }),
      h('path', { d: 'M8 16H3v5' }),
    ]),
  Layers: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('polygon', { points: '12 2 2 7 12 12 22 7 12 2' }),
      h('polyline', { points: '2 17 12 22 22 17' }),
      h('polyline', { points: '2 12 12 17 22 12' }),
    ]),
  Pause: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('rect', { x: '6', y: '4', width: '4', height: '16' }),
      h('rect', { x: '14', y: '4', width: '4', height: '16' }),
    ]),
  Play: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('polygon', { points: '5 3 19 12 5 21 5 3' }),
    ]),
  X: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('line', { x1: '18', y1: '6', x2: '6', y2: '18' }),
      h('line', { x1: '6', y1: '6', x2: '18', y2: '18' }),
    ]),
  Download: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('path', { d: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4' }),
      h('polyline', { points: '7 10 12 15 17 10' }),
      h('line', { x1: '12', y1: '15', x2: '12', y2: '3' }),
    ]),
  Trash2: (props) =>
    h('svg', { ...svgProps(props) }, [
      h('path', { d: 'M3 6h18' }),
      h('path', { d: 'M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6' }),
      h('path', { d: 'M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2' }),
      h('line', { x1: '10', y1: '11', x2: '10', y2: '17' }),
      h('line', { x1: '14', y1: '11', x2: '14', y2: '17' }),
    ]),
};

function svgProps(props = {}) {
  const size = props.size || 16;
  return {
    xmlns: 'http://www.w3.org/2000/svg',
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    'stroke-width': '2',
    'stroke-linecap': 'round',
    'stroke-linejoin': 'round',
    class: props.class || '',
  };
}

const App = {
  components: LucideIcons,
  setup() {
    const {
      fileQueue,
      isProcessing,
      addFiles,
      pauseItem,
      resumeItem,
      cancelItem,
      deleteFile,
      getDownloadUrl,
    } = useFileBridge();

    const fileInputRef = ref(null);
    const isDragActive = ref(false);

    const onDrop = (e) => {
      isDragActive.value = false;
      if (e.dataTransfer && e.dataTransfer.files.length) {
        addFiles(e.dataTransfer.files);
      }
    };

    const onSelect = (e) => {
      if (e.target && e.target.files.length) {
        addFiles(e.target.files);
        e.target.value = '';
      }
    };

    const formatBytes = (bytes) => {
      if (!bytes || bytes === 0) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
    };

    const activeThroughput = computed(() => {
      const active = fileQueue.value.find((i) => i.status === 'UPLOADING');
      return active ? active.speed : '0.0 MB/s';
    });

    const activeCount = computed(() => {
      return fileQueue.value.filter((i) => i.status === 'UPLOADING' || i.status === 'HASHING').length;
    });

    const handleDelete = async (item) => {
      if (item.fileId) {
        try {
          await deleteFile(item.fileId);
        } catch (err) {
          console.error(err);
        }
      }
      cancelItem(item);
    };

    return {
      fileQueue,
      isProcessing,
      fileInputRef,
      isDragActive,
      onDrop,
      onSelect,
      formatBytes,
      activeThroughput,
      activeCount,
      pauseItem,
      resumeItem,
      cancelItem,
      handleDelete,
      getDownloadUrl,
    };
  },
};

createApp(App).mount('#app');
