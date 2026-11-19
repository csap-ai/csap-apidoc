/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// 团队版控制台:构建产物输出到 web/dist,开发时把 /api 代理到后端 8090。
// mock 模式(npm run dev:mock)才会带上 MSW 的 service worker 文件,生产构建不含任何 mock。
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  publicDir: mode === 'mock' ? 'public-mock' : 'public',
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  server: {
    port: 5190,
    proxy: { '/api': { target: 'http://localhost:8090', changeOrigin: true } },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    testTimeout: 15000,
  },
}));
