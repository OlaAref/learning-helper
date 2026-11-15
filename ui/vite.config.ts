import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // the gateway serves the built UI
  build: { outDir: '../gateway/src/main/resources/static', emptyOutDir: true },
  // in dev, every API call goes through the gateway, exactly as in the built app
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
  test: { environment: 'jsdom' },
})
