import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [
    vue(),
    {
      // 确保 .wasm 文件获得正确的 MIME 类型（本地 static 文件用）
      name: 'wasm-mime-type-fix',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url && req.url.endsWith('.wasm')) {
            res.setHeader('Content-Type', 'application/wasm')
          }
          next()
        })
      }
    }
  ],
  server: {
    host: '0.0.0.0',   // 允许局域网访问
    port: 5173,
    headers: {
      // SharedArrayBuffer 需要 COEP/COOP
      'Cross-Origin-Embedder-Policy': 'credentialless',
      'Cross-Origin-Opener-Policy': 'same-origin',
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 代理萤石云CDN上的解码器文件，修正WASM的MIME类型
      '/ezuikit_cdn': {
        target: 'https://openstatic.ys7.com/ezuikit_js/v9.0.5/ezuikit_static',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/ezuikit_cdn/, ''),
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req) => {
            if (req.url && req.url.endsWith('.wasm')) {
              proxyRes.headers['content-type'] = 'application/wasm'
            }
          })
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': '/src'
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          // ezuikit-js 播放器库 → 独立 chunk（~3.5MB）
          if (id.includes('node_modules/ezuikit-js')) {
            return 'ezuikit'
          }
          // Vue/Vue Router/Pinia 框架 → 框架 chunk
          if (id.includes('node_modules/vue') ||
              id.includes('node_modules/vue-router') ||
              id.includes('node_modules/pinia')) {
            return 'framework'
          }
        }
      }
    },
    chunkSizeWarningLimit: 3600
  }
})
