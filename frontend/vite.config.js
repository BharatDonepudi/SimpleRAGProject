import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The Spring Boot backend must be running on :8080. Vite forwards /api to it,
// so the browser never needs CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
  },
})
