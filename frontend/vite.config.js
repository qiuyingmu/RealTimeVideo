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
  }
})
