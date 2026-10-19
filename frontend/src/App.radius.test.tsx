import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { act } from "react";
import { describe, expect, it, vi } from "vitest";
import type { Bbox } from "./api/client";
import { App } from "./App";
import { badBboxProblem, caesarDied, emptyEvents, fijiFounded, mozartBorn, problemJson } from "./test/apiFixtures";
import { createQueryWrapper } from "./test/queryWrapper";
import { server } from "./test/server";

type Place = { lat: number; lon: number; radius: number };

const mapView = vi.hoisted(() => ({
  onViewportChange: null as ((bbox: Bbox) => void) | null,
  onPlaceClick: null as ((place: { lat: number; lon: number; radius: number }) => void) | null,
  onPlaceRadiusChange: null as ((radius: number) => void) | null,
  place: undefined as { lat: number; lon: number; radius: number } | null | undefined,
}));

vi.mock("./map/MapView", () => ({
  MapView: (props: {
    onViewportChange: (bbox: Bbox) => void;
    onPlaceClick?: (place: Place) => void;
    onPlaceRadiusChange?: (radius: number) => void;
    place?: Place | null;
  }) => {
    mapView.onViewportChange = props.onViewportChange;
    mapView.onPlaceClick = props.onPlaceClick ?? null;
    mapView.onPlaceRadiusChange = props.onPlaceRadiusChange ?? null;
    mapView.place = props.place;
    return <section aria-label="Map" />;
  },
}));

const europe: Bbox = { minLon: -10, minLat: 35, maxLon: 30, maxLat: 60 };
const rome: Place = { lat: 41.9, lon: 12.5, radius: 2293 };
const fiji: Place = { lat: -17.8, lon: 179.9, radius: 12345 };
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function recordNearby(
  respond: (url: URL) => Response | Promise<Response> = () =>
    HttpResponse.json({ events: [caesarDied, mozartBorn], truncated: false }),
) {
  const urls: URL[] = [];
  server.use(
    http.get("*/api/events", () => HttpResponse.json(emptyEvents)),
    http.get("*/api/stats/timeline", () => HttpResponse.json({ decades: [] })),
    http.get("*/api/places/nearby", ({ request }) => {
      const url = new URL(request.url);
      urls.push(url);
      return respond(url);
    }),
  );
  return urls;
}

async function openNearby(place: Place = rome) {
  render(<App />, { wrapper: createQueryWrapper() });
  const report = mapView.onViewportChange;
  if (report === null) throw new Error("MapView was not given onViewportChange");
  act(() => report(europe));
  const click = mapView.onPlaceClick;
  if (click === null) throw new Error("MapView was not given onPlaceClick");
  act(() => click(place));
  return screen.findByRole("complementary", { name: "What happened here" });
}

function dragTo(radius: number) {
  const change = mapView.onPlaceRadiusChange;
  if (change === null) throw new Error("MapView was not given onPlaceRadiusChange");
  act(() => change(radius));
}

const radii = (urls: readonly URL[]) => urls.map((u) => u.searchParams.get("radius"));

describe("App resizing the what-happened-here circle", () => {
  it("passes onPlaceRadiusChange to the map while a place is shown", async () => {
    recordNearby();
    await openNearby();
    expect(mapView.onPlaceRadiusChange).toBeTypeOf("function");
  });

  it("resizes the circle at the same centre immediately when the handle is dragged", async () => {
    recordNearby();
    await openNearby();

    dragTo(10000);

    expect(mapView.place).toEqual({ lat: 41.9, lon: 12.5, radius: 10000 });
  });

  it("requests the new radius at the same centre and follows it in the Within text", async () => {
    const urls = recordNearby();
    const panel = await openNearby();
    await waitFor(() => expect(urls).toHaveLength(1));
    await sleep(300);

    dragTo(10000);

    await waitFor(() => expect(radii(urls)).toContain("10000"));
    const q = urls.at(-1)?.searchParams;
    expect(Number(q?.get("lat"))).toBe(41.9);
    expect(Number(q?.get("lon"))).toBe(12.5);
    expect(within(panel).getByText("Within 10 km")).toBeInTheDocument();
  });

  it("keeps an antimeridian centre when resizing", async () => {
    const urls = recordNearby();
    await openNearby(fiji);
    await sleep(300);

    dragTo(40000);

    expect(mapView.place).toEqual({ ...fiji, radius: 40000 });
    await waitFor(() => expect(radii(urls)).toContain("40000"));
    expect(Number(urls.at(-1)?.searchParams.get("lon"))).toBe(179.9);
  });

  it("throttles requests during a drag: the circle follows every step, the last radius is requested, not every step", async () => {
    const urls = recordNearby();
    await openNearby();
    await waitFor(() => expect(urls).toHaveLength(1));
    await sleep(300);
    const before = urls.length;

    for (let i = 1; i <= 20; i++) {
      dragTo(2293 + i * 1000);
      expect(mapView.place?.radius).toBe(2293 + i * 1000);
      await sleep(20);
    }

    await waitFor(() => expect(radii(urls)).toContain("22293"));
    await sleep(400);
    const during = urls.slice(before);
    expect(during.length).toBeGreaterThanOrEqual(2);
    expect(during.length).toBeLessThan(8);
    expect(radii(urls).at(-1)).toBe("22293");
  });

  it("keeps the list following a long drag, not only requesting where it ends", async () => {
    const urls = recordNearby();
    await openNearby();
    await waitFor(() => expect(urls).toHaveLength(1));
    await sleep(300);
    const before = urls.length;

    for (let i = 1; i <= 30; i++) {
      dragTo(3000 + i * 100);
      await sleep(20);
    }

    expect(urls.length - before).toBeGreaterThanOrEqual(2);
  });

  it("shows the newest radius's list when an older radius's response arrives later", async () => {
    const urls = recordNearby(async (url) => {
      if (url.searchParams.get("radius") === "50000") {
        await delay(300);
        return HttpResponse.json({ events: [caesarDied], truncated: false });
      }
      if (url.searchParams.get("radius") === "60000") {
        return HttpResponse.json({ events: [fijiFounded], truncated: false });
      }
      return HttpResponse.json({ events: [mozartBorn], truncated: false });
    });
    const panel = await openNearby();
    await within(panel).findByRole("button", { name: /^Q254/ });
    await sleep(300);

    dragTo(50000);
    await waitFor(() => expect(radii(urls)).toContain("50000"));
    await sleep(260);
    dragTo(60000);

    expect(await within(panel).findByRole("button", { name: /^Some village/ })).toBeInTheDocument();
    await sleep(400);
    expect(within(panel).getByRole("button", { name: /^Some village/ })).toBeInTheDocument();
    expect(within(panel).queryByRole("button", { name: /^Julius Caesar/ })).not.toBeInTheDocument();
  });

  it("shows a 400 problem's detail for a resized search inside the panel", async () => {
    recordNearby((url) =>
      url.searchParams.get("radius") === "70000"
        ? HttpResponse.json(
          { ...badBboxProblem, detail: "radius must be between 1 and 500000", instance: "/places/nearby" },
          { status: 400, headers: problemJson },
        )
        : HttpResponse.json({ events: [caesarDied], truncated: false }));
    const panel = await openNearby();
    await sleep(300);

    dragTo(70000);

    expect(await within(panel).findByRole("alert")).toHaveTextContent("radius must be between 1 and 500000");
  });

  it("resizes from the panel's Search radius slider and keeps focus on the slider", async () => {
    const urls = recordNearby();
    const panel = await openNearby();
    await waitFor(() => expect(urls).toHaveLength(1));
    await sleep(300);
    const slider = within(panel).getByRole("slider", { name: "Search radius" });
    slider.focus();

    fireEvent.change(slider, { target: { value: slider.getAttribute("max") ?? "100" } });

    expect(mapView.place).toEqual({ lat: 41.9, lon: 12.5, radius: 500000 });
    await waitFor(() => expect(radii(urls)).toContain("500000"));
    await sleep(100);
    expect(screen.getByText("Within 500 km")).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Search radius" })).toHaveFocus();
  });
});
