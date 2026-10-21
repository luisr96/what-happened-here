import { defineConfig, devices } from "@playwright/test";
import { ABOUT_SEEN_KEY } from "./src/about/aboutSeen";

// End-to-end flows run against the Vite dev server; tests mock the API (and map tiles) with
// page.route, so no backend or network is needed.
//
// The tests start their own server on a port of their own and never reuse a running one: a
// developer's long-running `npm run dev` can miss file changes (seen with Vite's watcher on
// Windows) and serve stale modules, making tests fail, or pass, against old code.
const PORT = 5174;
const BASE_URL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: "./e2e",
  use: {
    baseURL: BASE_URL,
    // Every test is a returning visitor: the About dialog opens by itself on a first visit and
    // would sit over the map and panel. Its own first-visit behaviour is covered in Vitest.
    storageState: {
      cookies: [],
      origins: [{ origin: BASE_URL, localStorage: [{ name: ABOUT_SEEN_KEY, value: "1" }] }],
    },
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: `npm run dev -- --port ${PORT} --strictPort`,
    url: BASE_URL,
    reuseExistingServer: false,
  },
});
