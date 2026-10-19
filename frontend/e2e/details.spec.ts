import { expect, test } from "@playwright/test";
import type { Page, Request } from "@playwright/test";
import type { components } from "../src/api/schema";
import { caesarDetails, caesarDied } from "../src/test/apiFixtures";

type EventView = components["schemas"]["EventView"];
type EventDetails = components["schemas"]["EventDetails"];

const STYLE_URL = "https://tiles.openfreemap.org/styles/dark";
const minimalStyle = {
  version: 8,
  sources: {},
  layers: [{ id: "background", type: "background", paint: { "background-color": "#111" } }],
};
const CORS = { "Access-Control-Allow-Origin": "*" };

/** Caesar's death moved to the map's initial center (lon 10, lat 30), so clicking the center hits it. */
const centred: EventView = { ...caesarDied, longitude: 10, latitude: 30, importance: 1000 };
const centredDetails: EventDetails = { ...caesarDetails, event: centred };

const isDetailsRequest = (request: Request) =>
  decodeURIComponent(new URL(request.url()).pathname) === `/api/events/${centred.id}`;

async function setup(page: Page) {
  await page.route(/^https?:\/\/(?!localhost)/, (route) => route.abort());
  await page.route("https://tiles.openfreemap.org/**", (route) =>
    route.request().url() === STYLE_URL
      ? route.fulfill({ contentType: "application/json", headers: CORS, body: JSON.stringify(minimalStyle) })
      : route.fulfill({ status: 404, headers: CORS, body: "" }),
  );
  await page.route("**/api/events?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ events: [centred], truncated: false }) }),
  );
  await page.route("**/api/stats/timeline?**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ decades: [] }) }),
  );
  await page.route("**/api/events/*", (route) =>
    isDetailsRequest(route.request())
      ? route.fulfill({ contentType: "application/json", body: JSON.stringify(centredDetails) })
      : route.fulfill({
        status: 404,
        contentType: "application/problem+json",
        body: JSON.stringify({ type: "about:blank", title: "Not Found", status: 404, detail: "No such event", instance: "/events" }),
      }),
  );
}

test("clicking an event opens its details, and Escape closes them", async ({ page }) => {
  await setup(page);
  const eventsLoaded = page.waitForResponse((r) => new URL(r.url()).pathname === "/api/events");
  await page.goto("/");
  const map = page.getByRole("region", { name: "Map" });
  await expect(map).toHaveAttribute("aria-busy", "false");
  await eventsLoaded;

  const box = await map.boundingBox();
  if (box === null) throw new Error("map has no bounding box");
  const detailsRequest = page.waitForRequest(isDetailsRequest);
  await expect(async () => {
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await expect(page.getByRole("complementary", { name: "Event details" })).toBeVisible({ timeout: 500 });
  }).toPass({ timeout: 10_000 });

  await detailsRequest;
  const panel = page.getByRole("complementary", { name: "Event details" });
  await expect(panel.getByRole("heading", { level: 2, name: "Julius Caesar" })).toBeVisible();
  await expect(panel).toContainText("Died 15 March 44 BC (Julian)");
  await expect(panel.getByRole("link", { name: "View on Wikidata" })).toHaveAttribute(
    "href",
    "https://www.wikidata.org/wiki/Q1048",
  );

  await panel.getByRole("button", { name: "Close details" }).focus();
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
});
