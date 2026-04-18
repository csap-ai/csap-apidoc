import { defineConfig, Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import svgr from 'vite-plugin-svgr'
import path from 'path'
import fs from 'fs'

// 自定义插件：重命名 HTML 文件
function renameIndexPlugin(): Plugin {
  return {
    name: 'rename-index-html',
    closeBundle() {
      const indexPath = path.resolve(__dirname, 'dist/index.html')
      const newPath = path.resolve(__dirname, 'dist/csap-api-devtools.html')

      if (fs.existsSync(indexPath)) {
        fs.renameSync(indexPath, newPath)
        console.log('✅ 已重命名: index.html -> csap-api-devtools.html')
      }
    },
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    svgr({
      svgrOptions: {
        icon: true,
      },
    }),
    renameIndexPlugin(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 9528,
    open: true,
    proxy: {
      // 只代理后端API路径，不拦截前端路由 /api
      '^/api/(csap|devtools|actuator)': {
        target: 'http://localhost:8182',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets2',
    sourcemap: false,
    // 使用 terser 移除生产环境的 console 和 debugger
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'antd-vendor': ['antd', '@ant-design/icons'],
        },
      },
    },
  },
})

