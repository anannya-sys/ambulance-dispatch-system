import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development the dashboard runs on :5173 and proxies API + WebSocket calls to Spring Boot on :8080.
// In Docker the built files are served by Spring Boot itself, so everything is on :8080.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  build: { outDir: 'dist', chunkSizeWarningLimit: 1500 },
})
