import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { act } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Bbox } from "./api/client";
import { ABOUT_SEEN_KEY } from "./about/aboutSeen";
import { App } from "./App";
import { caesarDied, emptyEvents } from "./test/apiFixtures";
import { createQueryWrapper } from "./test/queryWrapper";
import { server } from "./test/server";

type Place = { lat: number; lon: number; radius: number };

const mapView = vi.hoisted(() => ({
  onViewportChange: null as ((bbox: Bbox) => void) | null,
  onPlaceClick: null as ((place: { lat: number; lon: number; radius: number }) => void) | null,
  mapStyleUrl: undefined as string | undefined,
}));

vi.mock("./map/MapView", () => ({
  MapView: (props: { onViewportChange: (bbox: Bbox) => void; onPlaceClick?: (place: Place) => void; mapStyleUrl?: string }) => {
    mapView.onViewportChange = props.onViewportChange;
    mapView.onPlaceClick = props.onPlaceClick ?? null;
    mapView.mapStyleUrl = props.mapStyleUrl;
    return <section aria-label="Map" />;
  },
}));

const KEY = "whh.settings";
const rome: Place = { lat: 41.9, lon: 12.5, radius: 2293 };

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem(ABOUT_SEEN_KEY, "1");
  mapView.mapStyleUrl = undefined;
  server.use(
    http.get("*/api/events", () => HttpResponse.json(emptyEvents)),
    http.get("*/api/stats/timeline", () => HttpResponse.json({ decades: [] })),
    http.get("*/api/places/nearby", () => HttpResponse.json({ events: [caesarDied], truncated: false })),
  );
});

const renderApp = () => render(<App />, { wrapper: createQueryWrapper() });

async function choose(name: string) {
  const user = userEvent.setup();
  // The gear toggles, so it's only clicked when the popover isn't already open.
  const gear = screen.getByRole("button", { name: "Settings" });
  if (gear.getAttribute("aria-expanded") !== "true") await user.click(gear);
  await user.click(screen.getByRole("radio", { name }));
}

async function openNearby() {
  const report = mapView.onViewportChange;
  const click = mapView.onPlaceClick;
  if (report === null || click === null) throw new Error("MapView callbacks missing");
  act(() => report({ minLon: -10, minLat: 35, maxLon: 30, maxLat: 60 }));
  act(() => click(rome));
  return screen.findByRole("complementary", { name: "What happened here" });
}

describe("App settings", () => {
  it("passes the liberty OpenFreeMap style to the map by default", () => {
    renderApp();
    expect(mapView.mapStyleUrl).toBe("https://tiles.openfreemap.org/styles/liberty");
  });

  it("has the Settings gear in the left panel", () => {
    renderApp();
    const side = screen.getByRole("complementary", { name: "Filters" });
    expect(within(side).getByRole("button", { name: "Settings" })).toBeInTheDocument();
  });

  it("passes the Positron style URL to the map when Positron is chosen", async () => {
    renderApp();
    await choose("Positron");
    expect(mapView.mapStyleUrl).toBe("https://tiles.openfreemap.org/styles/positron");
  });

  it("shows the nearby panel's distance in miles when Miles is chosen", async () => {
    renderApp();
    const panel = await openNearby();
    expect(within(panel).getByText("Within 2.3 km")).toBeInTheDocument();

    await choose("mi");

    expect(within(panel).getByText("Within 1.4 mi")).toBeInTheDocument();
    expect(within(panel).getByRole("slider", { name: "Search radius" })).toHaveAttribute("aria-valuetext", "1.4 mi");
  });

  it("keeps the chosen settings after a remount", async () => {
    const first = renderApp();
    await choose("Positron");
    await choose("mi");
    first.unmount();
    mapView.mapStyleUrl = undefined;

    renderApp();

    expect(mapView.mapStyleUrl).toBe("https://tiles.openfreemap.org/styles/positron");
    const panel = await openNearby();
    expect(within(panel).getByText("Within 1.4 mi")).toBeInTheDocument();
  });

  it("starts from settings already in localStorage", () => {
    localStorage.setItem(KEY, JSON.stringify({ units: "mi", mapStyle: "fiord" }));
    renderApp();
    expect(mapView.mapStyleUrl).toBe("https://tiles.openfreemap.org/styles/fiord");
  });

  it("starts with the defaults when localStorage holds garbage", () => {
    localStorage.setItem(KEY, "{oops");
    renderApp();
    expect(mapView.mapStyleUrl).toBe("https://tiles.openfreemap.org/styles/liberty");
  });
});
