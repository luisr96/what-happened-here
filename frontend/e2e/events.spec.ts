import { expect, test } from "@playwright/test";
import type { Page, Request } from "@playwright/test";
import type { components } from "../src/api/schema";
import { caesarDied, fijiFounded, lisbonEarthquake } from "../src/test/apiFixtures";

type EventsResponse = components["schemas"]["EventsResponse"];

const STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
const minimalStyle = {
  version: 8,
  sources: {},
  layers: [{ id: "background", type: "background", paint: { "background-color": "#111" } }],
};
const CORS = { "Access-Control-Allow-Origin": "*" };
const TRUNCATED_HINT = "Showing the most important events here. Zoom in or raise the minimum importance to see more.";

const isEventsRequest = (request: Request) => new URL(request.url()).pathname === "/api/events";

async function setup(page: Page, response: EventsResponse) {
  await page.route(/^https?:\/\/(?!localhost)/, (route) => route.abort());
  await page.route("https://tiles.openfreemap.org/**", (route) =>
    route.request().url() === STYLE_URL
      ? route.fulfill({ contentType: "application/json", headers: CORS, body: JSON.stringify(minimalStyle) })
      : route.fulfill({ status: 404, headers: CORS, body: "" }),
  );
  await page.route("**/api/events?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify(response) }),
  );
  await page.route("**/api/stats/timeline?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ decades: [] }) }),
  );
}

test("requests events for the loaded map's viewport", async ({ page }) => {
  await setup(page, { events: [caesarDied, lisbonEarthquake], truncated: false });
  const eventsRequest = page.waitForRequest(isEventsRequest);
  await page.goto("/");

  await expect(page.getByRole("region", { name: "Map" })).toHaveAttribute("aria-busy", "false");
  const url = new URL((await eventsRequest).url());
  const bbox = (url.searchParams.get("bbox") ?? "").split(",").map(Number);
  expect(bbox).toHaveLength(4);
  for (const n of bbox) expect(Number.isFinite(n)).toBe(true);
  const [, minLat, , maxLat] = bbox;
  expect(minLat).toBeGreaterThanOrEqual(-90);
  expect(maxLat).toBeLessThanOrEqual(90);
  expect(url.searchParams.has("kinds")).toBe(false);
  await expect(page.getByText(TRUNCATED_HINT)).toBeHidden();
});

test("shows no notice for a truncated response", async ({ page }) => {
  const loaded = page.waitForResponse((r) => new URL(r.url()).pathname === "/api/events");
  await setup(page, { events: [fijiFounded], truncated: true });
  await page.goto("/");
  await loaded;

  await expect(page.getByText(TRUNCATED_HINT)).toHaveCount(0);
});

test("unchecking Died requests events without DIED", async ({ page }) => {
  await setup(page, { events: [caesarDied], truncated: false });
  const first = page.waitForRequest(isEventsRequest);
  await page.goto("/");
  await expect(page.getByRole("region", { name: "Map" })).toHaveAttribute("aria-busy", "false");
  await first;

  const filtered = page.waitForRequest(
    (r) => isEventsRequest(r) && new URL(r.url()).searchParams.has("kinds"),
  );
  await page.getByRole("checkbox", { name: "Died" }).uncheck();

  const kinds = new URL((await filtered).url()).searchParams.get("kinds")?.split(",");
  expect(kinds).toEqual(["HAPPENED", "BORN", "FOUNDED", "ENDED", "OPENED"]);
});

test("choosing Iconic requests events with minImportance=100", async ({ page }) => {
  await setup(page, { events: [caesarDied], truncated: false });
  const first = page.waitForRequest(isEventsRequest);
  await page.goto("/");
  await expect(page.getByRole("region", { name: "Map" })).toHaveAttribute("aria-busy", "false");
  await first;

  const filtered = page.waitForRequest(
    (r) => isEventsRequest(r) && new URL(r.url()).searchParams.get("minImportance") === "100",
  );
  const importance = page.getByRole("radiogroup", { name: "Show" });
  await importance.getByRole("radio", { name: "Iconic" }).check();

  await filtered;
  await expect(importance.getByRole("radio", { name: "Iconic" })).toBeChecked();
  await expect(page.getByRole("group", { name: "Events" }).getByRole("checkbox")).toHaveCount(6);
});
