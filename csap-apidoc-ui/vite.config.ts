import {defineConfig, loadEnv, ConfigEnv, UserConfig} from 'vite';
import {resolve} from 'path';
import {wrapperEnv} from './src/utils/getEnv';
import {visualizer} from 'rollup-plugin-visualizer';
import {createHtmlPlugin} from 'vite-plugin-html';
import viteCompression from 'vite-plugin-compression';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig((mode: ConfigEnv): UserConfig => {
    const env = loadEnv(mode.mode, process.cwd());
    const viteEnv = wrapperEnv(env);
    return {
        resolve: {
            alias: {
                '@': resolve(__dirname, './src'),
            },
        },
        // global css
        css: {
            preprocessorOptions: {
                less: {
                    // modifyVars: {
                    //   'primary-color': '#9373ee',
                    // },
                    javascriptEnabled: true,
                    additionalData: `@import "@/styles/var.less";`,
                },
            },
        },
        // server config
        server: {
            host: '0.0.0.0', // 服务器主机名，如果允许外部访问，可设置为"0.0.0.0"
            port: viteEnv.VITE_PORT,
            open: viteEnv.VITE_OPEN,
            cors: true,
            // https: false,
            proxy: {
                '/api': {
                    target: 'http://localhost:9281/',
                    changeOrigin: true,
                    rewrite: (path) => path.replace(/^\/api/, ''),
                },
                '/zm-admin-api': {
                    target: 'http://localhost:9281/',
                    changeOrigin: true,
                },
                '/zm-app-api': {
                    target: 'http://localhost:9281/',
                    changeOrigin: true,
                },
            },
        },
        plugins: [
            react(),
            createHtmlPlugin({
                inject: {
                    data: {
                        title: viteEnv.VITE_GLOB_APP_TITLE,
                    },
                },
            }),
            // * 是否生成包预览
            viteEnv.VITE_REPORT && visualizer(),
            // * gzip compress
            viteEnv.VITE_BUILD_GZIP &&
            viteCompression({
                verbose: true,
                disable: false,
                threshold: 10240,
                algorithm: 'gzip',
                ext: '.gz',
            }),
        ],
        // 开发环境保留 console，生产环境移除
        esbuild: {
            drop: mode.mode === 'production' ? ['console', 'debugger'] : [],
        },
        optimizeDeps: {
            include: ['js-yaml'],
        },
        // build configure
        build: {
            outDir: 'dist',
            // 使用 terser 彻底移除 console 和 debugger
            minify: 'terser',
            terserOptions: {
                compress: {
                    drop_console: true,
                    drop_debugger: true,
                },
            },
            rollupOptions: {
                output: {
                    // Static resource classification and packaging
                    chunkFileNames: 'assets/js/[name]-[hash].js',
                    entryFileNames: 'assets/js/[name]-[hash].js',
                    assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',

                },
            },
        },
    };
});
