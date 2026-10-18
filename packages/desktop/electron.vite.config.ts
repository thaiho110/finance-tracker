import { resolve } from 'node:path';
import { defineConfig, externalizeDepsPlugin } from 'electron-vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/** The renderer IS the web app (ADR-011): same sources, same build, plus the desktop bridge at runtime. */
const webRoot = resolve(__dirname, '../web');

export default defineConfig({
  main: {
    plugins: [externalizeDepsPlugin()],
    build: { outDir: 'out/main', rollupOptions: { input: resolve(__dirname, 'src/main/index.ts') } },
  },
  preload: {
    plugins: [externalizeDepsPlugin()],
    build: { outDir: 'out/preload', rollupOptions: { input: resolve(__dirname, 'src/preload/index.ts') } },
  },
  renderer: {
    root: webRoot,
    publicDir: resolve(webRoot, 'public'),
    plugins: [react(), tailwindcss()],
    resolve: { alias: { '@': resolve(webRoot, 'src') } },
    server: {
      port: 5174,
      // Dev only: same proxy as the web app. Production uses the app:// protocol in the main process.
      proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
    },
    build: {
      outDir: resolve(__dirname, 'out/renderer'),
      emptyOutDir: true,
      minify: true,
      rollupOptions: { input: resolve(webRoot, 'index.html') },
    },
  },
});
