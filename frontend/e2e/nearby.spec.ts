import { expect, test } from "@playwright/test";
import type { Page, Request } from "@playwright/test";
import type { components } from "../src/api/schema";
import { caesarDetails, caesarDied, mozartBorn, mozartDetailsNoWikipedia } from "../src/test/apiFixtures";

type EventsResponse = components["schemas"]["EventsResponse"];
type EventDetails = components["schemas"]["EventDetails"];

const STYLE_URL = "https://tiles.openfreemap.org/styles/dark";
const minimalStyle = {
  version: 8,
  sources: {},
  layers: [{ id: "background", type: "background", paint: { "background-color": "#111" } }],
};
const CORS = { "Access-Control-Allow-Origin": "*" };

const nearby: EventsResponse = { events: [caesarDied, mozartBorn], truncated: false };
const details: Readonly<Record<string, EventDetails>> = {
  [caesarDetails.event.id]: caesarDetails,
  [mozartDetailsNoWikipedia.event.id]: mozartDetailsNoWikipedia,
};

const isNearbyRequest = (request: Request) => new URL(request.url()).pathname === "/api/places/nearby";

async function setup(page: Page) {
  await page.route(/^https?:\/\/(?!localhost)/, (route) => route.abort());
  await page.route("https://tiles.openfreemap.org/**", (route) =>
    route.request().url() === STYLE_URL
      ? route.fulfill({ contentType: "application/json", headers: CORS, body: JSON.stringify(minimalStyle) })
      : route.fulfill({ status: 404, headers: CORS, body: "" }),
  );
  await page.route("**/api/events?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ events: [], truncated: false }) }),
  );
  await page.route("**/api/stats/timeline?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ decades: [] }) }),
  );
  await page.route("**/api/places/nearby?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify(nearby) }),
  );
  await page.route("**/api/events/*", (route) => {
    const id = decodeURIComponent(new URL(route.request().url()).pathname.replace("/api/events/", ""));
    const body = details[id];
    return body
      ? route.fulfill({ contentType: "application/json", body: JSON.stringify(body) })
      : route.fulfill({
        status: 404,
        contentType: "application/problem+json",
        body: JSON.stringify({ type: "about:blank", title: "Not Found", status: 404, detail: `No event ${id}`, instance: `/events/${id}` }),
      });
  });
}

test("clicking an empty spot lists what happened there, and a listed event opens with Back to list", async ({ page }) => {
  await setup(page);
  const eventsLoaded = page.waitForResponse((r) => new URL(r.url()).pathname === "/api/events");
  await page.goto("/");
  const map = page.getByRole("region", { name: "Map" });
  await expect(map).toHaveAttribute("aria-busy", "false");
  await eventsLoaded;

  const box = await map.boundingBox();
  if (box === null) throw new Error("map has no bounding box");
  const nearbyRequest = page.waitForRequest(isNearbyRequest);
  const panel = page.getByRole("complementary", { name: "What happened here" });
  await expect(async () => {
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await expect(panel).toBeVisible({ timeout: 500 });
  }).toPass({ timeout: 10_000 });

  const params = new URL((await nearbyRequest).url()).searchParams;
  expect(Number.isFinite(Number(params.get("lat")))).toBe(true);
  expect(Number.isFinite(Number(params.get("lon")))).toBe(true);
  const radius = Number(params.get("radius"));
  expect(Number.isInteger(radius)).toBe(true);
  expect(radius).toBeGreaterThanOrEqual(1);
  expect(radius).toBeLessThanOrEqual(500000);

  const list = panel.getByRole("list");
  await expect(list.getByRole("button")).toHaveCount(2);
  await expect(list.getByRole("button").first()).toContainText("Julius Caesar");
  await expect(list.getByRole("button").first()).toContainText("Died 15 March 44 BC (Julian)");
  await expect(list.getByRole("button").nth(1)).toContainText("Q254");

  await list.getByRole("button").first().click();
  const detailsPanel = page.getByRole("complementary", { name: "Event details" });
  await expect(detailsPanel.getByRole("heading", { level: 2, name: "Julius Caesar" })).toBeVisible();

  await detailsPanel.getByRole("button", { name: "Back to list" }).click();
  await expect(panel.getByRole("list").getByRole("button")).toHaveCount(2);
  await expect(detailsPanel).toBeHidden();
});

test("dragging the map pans it without searching what happened there", async ({ page }) => {
  await setup(page);
  let nearbyRequests = 0;
  page.on("request", (r) => {
    if (r.url().includes("/api/places/nearby")) nearbyRequests++;
  });
  await page.goto("/");
  const map = page.getByRole("region", { name: "Map" });
  await expect(map).toHaveAttribute("aria-busy", "false");
  const box = await map.boundingBox();
  if (box === null) throw new Error("map has no box");
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;

  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + 50, y + 20, { steps: 5 });
  await page.mouse.up();
  await page.waitForTimeout(500);

  expect(nearbyRequests).toBe(0);
  await expect(page.getByRole("complementary", { name: "What happened here" })).toHaveCount(0);
});

test("pressing ArrowRight on Search radius searches a larger area", async ({ page }) => {
  await setup(page);
  await page.goto("/");
  const map = page.getByRole("region", { name: "Map" });
  await expect(map).toHaveAttribute("aria-busy", "false");
  const box = await map.boundingBox();
  if (box === null) throw new Error("map has no bounding box");
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;
  // Zoom in first: at the start zoom a click already searches the API's maximum of 500 km.
  await page.mouse.move(x, y);
  for (let i = 0; i < 6; i++) {
    await page.mouse.wheel(0, -500);
    await page.waitForTimeout(150);
  }
  await page.waitForTimeout(800);

  const firstRequest = page.waitForRequest(isNearbyRequest);
  const panel = page.getByRole("complementary", { name: "What happened here" });
  await expect(async () => {
    await page.mouse.click(x, y);
    await expect(panel).toBeVisible({ timeout: 500 });
  }).toPass({ timeout: 10_000 });
  const firstRadius = Number(new URL((await firstRequest).url()).searchParams.get("radius"));
  expect(firstRadius).toBeLessThan(500000);

  const slider = panel.getByRole("slider", { name: "Search radius" });
  const larger = page.waitForRequest(
    (r) => isNearbyRequest(r) && Number(new URL(r.url()).searchParams.get("radius")) > firstRadius,
  );
  await slider.focus();
  for (let i = 0; i < 5; i++) await slider.press("ArrowRight");

  const radius = Number(new URL((await larger).url()).searchParams.get("radius"));
  expect(Number.isInteger(radius)).toBe(true);
  expect(radius).toBeLessThanOrEqual(500000);
  await expect(slider).toBeFocused();
});
