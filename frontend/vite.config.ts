import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ['vue', 'vue-router', 'pinia'],
        dts: 'auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      open: true,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
          // SSE 流式：不代理超时
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => {
              proxyReq.setHeader('Accept', 'text/event-stream')
            })
          },
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: mode !== 'production',
      // v1.11.0 优化：chunk 大小警告阈值调整为 800KB，便于发现过大的 chunk
      chunkSizeWarningLimit: 800,
      rollupOptions: {
        output: {
          manualChunks: {
            // Vue 核心：vue + vue-router + pinia，约 70KB gzip
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            // Element Plus 组件库，约 180KB gzip
            'element-vendor': ['element-plus', '@element-plus/icons-vue'],
            // Markdown 渲染：markdown-it + highlight.js/core + katex
            // v1.11.0 优化：highlight.js 改为按需导入，此 chunk 从 ~1MB 降至 ~80KB
            'markdown-vendor': ['markdown-it', 'highlight.js/lib/core', 'katex'],
          },
        },
      },
    },
  }
})
