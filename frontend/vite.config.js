import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true, // Mở host 0.0.0.0 để Docker map được port
    port: 5173,
    strictPort: true,
    watch: {
      usePolling: true, // Bắt buộc cho Docker trên Windows/WSL để nhận diện file thay đổi
    },
  },
})