import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Dev server proxies /api to the Spring Boot backend so the frontend can call
// relative paths ("/api/...") in both dev and prod (prod uses VITE_API_BASE_URL instead).
// BACKEND_URL: where the Spring Boot backend runs in dev (default http://localhost:8081)
const backend = process.env.BACKEND_URL || 'http://localhost:8081'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: backend,
        changeOrigin: true
      },
      // realtime chat / notifications (STOMP over WebSocket)
      '/ws': {
        target: backend.replace(/^http/, 'ws'),
        ws: true
      },
      // uploaded product/review media served by Spring Boot
      '/uploads': {
        target: backend,
        changeOrigin: true
      }
    }
  }
})
