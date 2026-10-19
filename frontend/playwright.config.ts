import { defineConfig, devices } from "@playwright/test";

// End-to-end flows run against the Vite dev server; tests mock the API (and map tiles) with
// page.route, so no backend or network is needed.
export default defineConfig({
  testDir: "./e2e",
  use: { baseURL: "http://localhost:5173" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev -- --port 5173 --strictPort",
    url: "http://localhost:5173",
    reuseExistingServer: true,
  },
});
