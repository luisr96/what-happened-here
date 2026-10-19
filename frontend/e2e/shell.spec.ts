import { expect, test } from "@playwright/test";

const STYLE_URL = "https://tiles.openfreemap.org/styles/dark";
const TILEJSON_URL = "https://tiles.openfreemap.org/planet";

// Like the real style: the credit comes from the source's TileJSON, not from the app.
const minimalStyle = {
  version: 8,
  sources: { openmaptiles: { type: "vector", url: TILEJSON_URL } },
  layers: [
    { id: "background", type: "background", paint: { "background-color": "#111" } },
    // MapLibre credits only sources a layer uses.
    { id: "water", type: "fill", source: "openmaptiles", "source-layer": "water" },
  ],
};

const tileJson = {
  tilejson: "3.0.0",
  tiles: ["https://tiles.openfreemap.org/planet/test/{z}/{x}/{y}.pbf"],
  attribution:
    '<a href="https://openfreemap.org" target="_blank">OpenFreeMap</a> ' +
    '<a href="https://www.openmaptiles.org/" target="_blank">&copy; OpenMapTiles</a> ' +
    'Data from <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a>',
};

// MapLibre fetches the style cross-origin, so stubbed responses need a CORS header.
const CORS = { "Access-Control-Allow-Origin": "*" };

test.beforeEach(async ({ page }) => {
  // Playwright tries the most recently registered route first, so the catch-all comes first and
  // the OpenFreeMap stub after it.
  await page.route(/^https?:\/\/(?!localhost)/, (route) => route.abort());
  await page.route("https://tiles.openfreemap.org/**", (route) => {
    const json = { [STYLE_URL]: minimalStyle, [TILEJSON_URL]: tileJson }[route.request().url()];
    if (json) {
      return route.fulfill({ contentType: "application/json", headers: CORS, body: JSON.stringify(json) });
    }
    return route.fulfill({ status: 404, headers: CORS, body: "" });
  });
  await page.goto("/");
});

test("renders the map on a canvas with the OpenFreeMap style", async ({ page }) => {
  const styleRequest = page.waitForRequest(STYLE_URL);
  await page.reload();
  await styleRequest;

  const map = page.getByRole("region", { name: "Map" });
  await expect(map).toBeVisible();
  await expect(map.locator("canvas").first()).toBeVisible();
  // aria-busy turns false on MapLibre's load event, which needs the style to have loaded.
  await expect(map).toHaveAttribute("aria-busy", "false");
});

test("credits OpenFreeMap and OpenStreetMap", async ({ page }) => {
  const map = page.getByRole("region", { name: "Map" });
  await expect(map.getByText(/OpenFreeMap/)).toBeVisible();
  await expect(map.getByText(/OpenStreetMap/)).toBeVisible();
});

test("collapses and expands the side panel", async ({ page }) => {
  const panel = page.getByRole("complementary", { name: "Filters and layers" });
  await expect(panel.getByRole("heading", { name: "Filters" })).toBeVisible();

  const toggle = panel.getByRole("button", { name: "Filters and layers panel" });
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  await toggle.click();
  await expect(panel.getByRole("heading", { name: "Filters" })).toBeHidden();
  await expect(toggle).toHaveAttribute("aria-expanded", "false");

  await toggle.click();
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  await expect(panel.getByRole("heading", { name: "Filters" })).toBeVisible();
  await expect(panel.getByRole("heading", { name: "Layers" })).toBeVisible();
});

test("shows the timeline along the bottom", async ({ page }) => {
  await expect(page.getByRole("region", { name: "Timeline" })).toBeVisible();
});
