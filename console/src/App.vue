<script setup lang="ts">
import {
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  type GlobalThemeOverrides,
} from 'naive-ui';
import ConsoleWorkbench from './components/ConsoleWorkbench.vue';

// 亮色蓝白商务科技主题定制
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#2563eb',
    primaryColorHover: '#3b82f6',
    primaryColorPressed: '#1d4ed8',
    primaryColorSuppl: '#2563eb',
    textColorBase: '#0f172a',
    borderRadius: '8px',
  },
  Card: {
    color: '#ffffff',
    borderColor: '#e2e8f0',
  },
  DataTable: {
    headerColor: '#f8fafc',
    headerTextColor: '#475569',
    headerFontWeight: '600',
    borderColor: '#e2e8f0',
    tdColorHover: '#f8fafc',
  },
  Button: {
    borderRadiusMedium: '8px',
    borderRadiusSmall: '6px',
    borderRadiusTiny: '4px',
  },
  Input: {
    borderRadius: '8px',
    borderColor: '#cbd5e1',
    color: '#ffffff',
    colorFocus: '#ffffff',
  },
  Tag: {
    borderRadius: '6px',
  },
  Dialog: {
    borderRadius: '12px',
    titleTextColor: '#0f172a',
    contentTextColor: '#334155',
  },
};
</script>

<template>
  <NConfigProvider :theme-overrides="themeOverrides">
    <NMessageProvider>
      <NDialogProvider>
        <ConsoleWorkbench />
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
