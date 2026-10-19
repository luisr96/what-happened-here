import { expect, test } from "@playwright/test";
import type { Page, Request } from "@playwright/test";
import type { components } from "../src/api/schema";
import { caesarDied, mozartBorn } from "../src/test/apiFixtures";

type EventsResponse = components["schemas"]["EventsResponse"];

const STYLES = ["dark", "positron"].map((id) => `https://tiles.openfreemap.org/styles/${id}`);
const minimalStyle = {
  version: 8,
  sources: {},
  layers: [{ id: "background", type: "background", paint: { "background-color": "#111" } }],
};
const CORS = { "Access-Control-Allow-Origin": "*" };
const nearby: EventsResponse = { events: [caesarDied, mozartBorn], truncated: false };

async function setup(page: Page) {
  await page.route(/^https?:\/\/(?!localhost)/, (route) => route.abort());
  await page.route("https://tiles.openfreemap.org/**", (route) =>
    STYLES.includes(route.request().url())
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
}

async function openSettings(page: Page) {
  await page.getByRole("button", { name: "Settings" }).click();
  return page.getByRole("dialog", { name: "Settings" });
}

test("choosing Miles shows the nearby search distance in miles", async ({ page }) => {
  await setup(page);
  await page.goto("/");
  const map = page.getByRole("region", { name: "Map" });
  await expect(map).toHaveAttribute("aria-busy", "false");

  const settings = await openSettings(page);
  await settings.getByRole("radio", { name: "Miles" }).check();
  await page.keyboard.press("Escape");
  await expect(settings).toBeHidden();

  const box = await map.boundingBox();
  if (box === null) throw new Error("map has no bounding box");
  const panel = page.getByRole("complementary", { name: "What happened here" });
  await expect(async () => {
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await expect(panel).toBeVisible({ timeout: 500 });
  }).toPass({ timeout: 10_000 });

  await expect(panel.getByText(/^Within .* mi$/)).toBeVisible();
});

test("choosing Positron loads the Positron map style", async ({ page }) => {
  await setup(page);
  await page.goto("/");
  await expect(page.getByRole("region", { name: "Map" })).toHaveAttribute("aria-busy", "false");

  const positron = page.waitForRequest((r: Request) => r.url() === "https://tiles.openfreemap.org/styles/positron");
  const settings = await openSettings(page);
  await settings.getByRole("radio", { name: "Positron" }).check();

  await positron;
});
