import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: { assetsInlineLimit: 0 }, // Keep official SVG as same-origin asset compatible with production CSP.
  server: {
    port: 5173,
    strictPort: true,
    proxy: Object.fromEntries(['/api', '/oauth2', '/redirect', '/logout', '/auth'].map((path) =>
      [path, { target: 'http://127.0.0.1:18080', changeOrigin: false }])),
  },
  test: { environment: 'jsdom', globals: true, restoreMocks: true, setupFiles: ['./src/test/setup.ts'] },
});
