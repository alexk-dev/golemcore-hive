import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: process.env.HIVE_API_PROXY ?? 'http://localhost:8080',
        changeOrigin: Boolean(process.env.HIVE_API_PROXY),
        secure: true,
        configure: (proxy) => {
          if (!process.env.HIVE_API_PROXY) {
            return;
          }
          const origin = process.env.HIVE_API_PROXY;
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('origin', origin);
            proxyReq.setHeader('referer', `${origin}/`);
          });
          proxy.on('proxyRes', (proxyRes) => {
            const setCookie = proxyRes.headers['set-cookie'];
            if (Array.isArray(setCookie)) {
              proxyRes.headers['set-cookie'] = setCookie.map((c) =>
                c.replace(/;\s*Secure/gi, '').replace(/;\s*SameSite=\w+/gi, '; SameSite=Lax'),
              );
            }
          });
        },
      },
      '/actuator': {
        target: process.env.HIVE_API_PROXY ?? 'http://localhost:8080',
        changeOrigin: Boolean(process.env.HIVE_API_PROXY),
        secure: true,
      },
      '/ws': {
        target: (process.env.HIVE_API_PROXY ?? 'http://localhost:8080').replace(/^http/, 'ws'),
        ws: true,
        changeOrigin: Boolean(process.env.HIVE_API_PROXY),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    exclude: ['e2e/**', 'node_modules/**'],
  },
});
