import { expect, test } from "@playwright/test";
import type { Page, Request } from "@playwright/test";
import type { components } from "../src/api/schema";
import { caesarDied, lisbonEarthquake } from "../src/test/apiFixtures";

type TimelineResponse = components["schemas"]["TimelineResponse"];
type EventsResponse = components["schemas"]["EventsResponse"];

const STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
const minimalStyle = {
  version: 8,
  sources: {},
  layers: [{ id: "background", type: "background", paint: { "background-color": "#111" } }],
};
const CORS = { "Access-Control-Allow-Origin": "*" };

const timeline: TimelineResponse = {
  decades: [
    { decade: -50, count: 3 },
    { decade: 0, count: 2 },
    { decade: 1750, count: 12 },
    { decade: 1880, count: 919 },
  ],
};
const events: EventsResponse = { events: [caesarDied, lisbonEarthquake], truncated: false };

const isEventsRequest = (request: Request) => new URL(request.url()).pathname === "/api/events";

async function setup(page: Page) {
  await page.route(/^https?:\/\/(?!localhost)/, (route) => route.abort());
  await page.route("https://tiles.openfreemap.org/**", (route) =>
    route.request().url() === STYLE_URL
      ? route.fulfill({ contentType: "application/json", headers: CORS, body: JSON.stringify(minimalStyle) })
      : route.fulfill({ status: 404, headers: CORS, body: "" }),
  );
  await page.route("**/api/events?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify(events) }),
  );
  await page.route("**/api/stats/timeline?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify(timeline) }),
  );
}

test("shows the per-decade histogram for the viewport", async ({ page }) => {
  await setup(page);
  await page.goto("/");

  const histogram = page.getByRole("img", { name: "Events per decade" });
  await expect(histogram).toBeVisible();
  // Playwright's getByTitle reads only title attributes; a bar's name is its SVG <title> element.
  await expect(histogram.locator("title", { hasText: "1880 – 1889: 919 events" })).toBeAttached();
  await expect(histogram.locator("title", { hasText: "50 BC – 41 BC: 3 events" })).toBeAttached();
  await expect(page.getByText("All time", { exact: true }).first()).toBeVisible();
});

test("moving the From slider with the keyboard requests events from that year", async ({ page }) => {
  await setup(page);
  const first = page.waitForRequest(isEventsRequest);
  await page.goto("/");
  await expect(page.getByRole("region", { name: "Map" })).toHaveAttribute("aria-busy", "false");
  expect(new URL((await first).url()).searchParams.has("from")).toBe(false);

  const from = page.getByRole("slider", { name: "From" });
  await expect(from).toHaveAttribute("aria-valuemin", "-50");
  const ranged = page.waitForRequest((r) => isEventsRequest(r) && new URL(r.url()).searchParams.has("from"));
  await from.focus();
  for (let i = 0; i < 5; i++) await from.press("ArrowRight");

  const params = new URL((await ranged).url()).searchParams;
  const year = Number(params.get("from"));
  expect(year).toBeGreaterThan(-50);
  expect(year).toBeLessThan(0);
  expect(params.get("to")).toBe("1889");
  // Throttled: the first step is requested at once (49 BC); the handle ends five steps on.
  await expect(from).toHaveAttribute("aria-valuetext", "45 BC");
});

test("typing 44 BC into From year requests events from -44", async ({ page }) => {
  await setup(page);
  const first = page.waitForRequest(isEventsRequest);
  await page.goto("/");
  await first;

  const timelineRegion = page.getByRole("region", { name: "Timeline" });
  await expect(timelineRegion.getByRole("slider", { name: "From" })).toHaveAttribute("aria-valuemin", "-50");
  const era = timelineRegion.getByRole("button", { name: "From era" });
  if ((await era.textContent())?.trim() !== "BC") await era.click();
  await expect(era).toHaveText("BC");

  const ranged = page.waitForRequest((r) => isEventsRequest(r) && new URL(r.url()).searchParams.get("from") === "-44");
  const year = timelineRegion.getByRole("spinbutton", { name: "From year" });
  await year.fill("44");
  await year.press("Enter");

  const params = new URL((await ranged).url()).searchParams;
  expect(params.get("to")).toBe("1889");
  await expect(timelineRegion.getByRole("spinbutton", { name: "From year" })).toHaveValue("44");
  await expect(timelineRegion.getByRole("button", { name: "From era" })).toHaveText("BC");
});

test("zoom buttons are icon buttons found by name", async ({ page }) => {
  await setup(page);
  await page.goto("/");

  const timelineRegion = page.getByRole("region", { name: "Timeline" });
  const from = timelineRegion.getByRole("slider", { name: "From" });
  await expect(from).toHaveAttribute("aria-valuemin", "-50");

  const zoomIn = timelineRegion.getByRole("button", { name: "Zoom in" });
  await expect(zoomIn).toHaveAttribute("title", "Zoom in");
  await expect(timelineRegion.getByRole("button", { name: "Zoom out" })).toBeDisabled();
  await zoomIn.click();

  await expect(from).not.toHaveAttribute("aria-valuemin", "-50");
  const reset = timelineRegion.getByRole("button", { name: "Reset zoom" });
  await expect(reset).toBeEnabled();
  await reset.click();
  await expect(from).toHaveAttribute("aria-valuemin", "-50");
});

test("a timeline reaching the 2020s ends at the current year, not 2029", async ({ page }) => {
  await setup(page);
  const recent: TimelineResponse = { decades: [{ decade: 1880, count: 919 }, { decade: 2020, count: 4 }] };
  await page.route("**/api/stats/timeline?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify(recent) }),
  );
  await page.goto("/");

  const now = String(new Date().getFullYear());
  await expect(page.getByRole("slider", { name: "To" })).toHaveAttribute("aria-valuemax", now);
  await expect(page.getByRole("spinbutton", { name: "To year" })).toHaveValue(now);
});

test("a year box keeps at most 5 digits", async ({ page }) => {
  await setup(page);
  await page.goto("/");
  const toYear = page.getByRole("spinbutton", { name: "To year" });
  await expect(toYear).toHaveValue("1889");

  await toYear.fill("");
  await toYear.pressSequentially("123456");

  await expect(toYear).toHaveValue("12345");
  await expect(toYear).not.toHaveAttribute("aria-invalid", "true");
});
