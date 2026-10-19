import { defineConfig, devices } from "@playwright/test";

// End-to-end flows run against the Vite dev server; tests mock the API (and map tiles) with
// page.route, so no backend or network is needed.
//
// The tests start their own server on a port of their own and never reuse a running one: a
// developer's long-running `npm run dev` can miss file changes (seen with Vite's watcher on
// Windows) and serve stale modules, making tests fail, or pass, against old code.
const PORT = 5174;

export default defineConfig({
  testDir: "./e2e",
  use: { baseURL: `http://localhost:${PORT}` },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: `npm run dev -- --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: false,
  },
});
