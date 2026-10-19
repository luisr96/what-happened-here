/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// In development the API runs separately (./gradlew :api:bootRun, port 8080); /api is proxied to
// it so the app can use same-origin relative URLs everywhere.
export default defineConfig({
  plugins: [react()],
  // The path the site is served under: "/" by default; a GitHub Pages project site lives at
  // /<repo>/, so its build sets BASE_PATH=/what-happened-here/ (see .github/workflows/pages.yml).
  base: process.env["BASE_PATH"] ?? "/",
  // MapLibre 6 loads its web worker from its own package files; pre-bundling breaks that path.
  optimizeDeps: { exclude: ["maplibre-gl"] },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
