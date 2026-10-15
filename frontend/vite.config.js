import { defineConfig } from 'vitest/config';
export default defineConfig({
  build: { outDir: 'dist', emptyOutDir: true },
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
  test: { environment: 'happy-dom', setupFiles: './src/test-setup.js', maxWorkers: 2 },
});
