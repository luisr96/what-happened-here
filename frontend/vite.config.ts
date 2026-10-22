/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

// In development /api is proxied to an API so the app can use same-origin relative URLs
// everywhere: a local one by default (./gradlew :api:bootRun, port 8080), or any other set in
// API_PROXY_TARGET, e.g. the live https://what-happened-here.duckdns.org (proxied server-side, so
// its CORS rules don't apply). Put it in .env.local (git-ignored) to keep it across terminals; a
// variable set in the shell still wins. loadEnv is needed because Vite only reads .env files into
// import.meta.env for the app, not into process.env for this config.
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  // The path the site is served under: "/" by default; a GitHub Pages project site lives at
  // /<repo>/, so its build sets BASE_PATH=/what-happened-here/ (see .github/workflows/pages.yml).
  base: process.env["BASE_PATH"] ?? "/",
  // MapLibre 6 loads its web worker from its own package files; pre-bundling breaks that path.
  optimizeDeps: { exclude: ["maplibre-gl"] },
  server: {
    proxy: {
      "/api": {
        target: loadEnv(mode, import.meta.dirname, "API_PROXY_")["API_PROXY_TARGET"] ?? "http://localhost:8080",
        // Send the target's own host name: Caddy picks the site by it.
        changeOrigin: true,
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
}));
