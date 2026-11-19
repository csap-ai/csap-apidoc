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
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        // 稳定的第三方依赖单独分包,利于缓存;页面代码由路由懒加载拆开
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (/node_modules\/(react|react-dom|react-router|react-router-dom|scheduler)\//.test(id)) return 'vendor-react';
          if (/node_modules\/(diff|dayjs)\//.test(id)) return 'vendor-misc';
          if (/node_modules\/(@ant-design\/icons|@ant-design\/icons-svg)\//.test(id)) return 'vendor-icons';
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    testTimeout: 15000,
  },
}));
