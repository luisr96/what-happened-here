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
type Centre = { lat: number; lon: number };

const mapView = vi.hoisted(() => ({
  onViewportChange: null as ((bbox: Bbox) => void) | null,
  onPlaceClick: null as ((place: { lat: number; lon: number; radius: number }) => void) | null,
  onPlaceRadiusChange: null as ((radius: number) => void) | null,
  onPlaceMove: null as ((centre: { lat: number; lon: number }) => void) | null,
  place: undefined as { lat: number; lon: number; radius: number } | null | undefined,
}));

vi.mock("./map/MapView", () => ({
  MapView: (props: {
    onViewportChange: (bbox: Bbox) => void;
    onPlaceClick?: (place: Place) => void;
    onPlaceRadiusChange?: (radius: number) => void;
    onPlaceMove?: (centre: Centre) => void;
    place?: Place | null;
  }) => {
    mapView.onViewportChange = props.onViewportChange;
    mapView.onPlaceClick = props.onPlaceClick ?? null;
    mapView.onPlaceRadiusChange = props.onPlaceRadiusChange ?? null;
    mapView.onPlaceMove = props.onPlaceMove ?? null;
    mapView.place = props.place;
    return <section aria-label="Map" />;
  },
}));

const europe: Bbox = { minLon: -10, minLat: 35, maxLon: 30, maxLat: 60 };
const rome: Place = { lat: 41.9, lon: 12.5, radius: 2293 };
const fiji: Place = { lat: -17.8, lon: 179.9, radius: 12345 };
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const latOf = (url: URL) => Number(url.searchParams.get("lat"));

/** Rome's spot lists Caesar; anywhere else lists Mozart (unlabelled, Q254), unless `respond` says otherwise. */
function recordNearby(
  respond: (url: URL) => Response | Promise<Response> = (url) =>
    HttpResponse.json({ events: [latOf(url) === rome.lat ? caesarDied : mozartBorn], truncated: false }),
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

function renderApp() {
  render(<App />, { wrapper: createQueryWrapper() });
  const report = mapView.onViewportChange;
  if (report === null) throw new Error("MapView was not given onViewportChange");
  act(() => report(europe));
}

function click(place: Place) {
  const onClick = mapView.onPlaceClick;
  if (onClick === null) throw new Error("MapView was not given onPlaceClick");
  act(() => onClick(place));
}

async function openNearby(place: Place = rome) {
  renderApp();
  click(place);
  return screen.findByRole("complementary", { name: "What happened here" });
}

function moveTo(centre: Centre) {
  const move = mapView.onPlaceMove;
  if (move === null) throw new Error("MapView was not given onPlaceMove");
  act(() => move(centre));
}

function dragTo(radius: number) {
  const change = mapView.onPlaceRadiusChange;
  if (change === null) throw new Error("MapView was not given onPlaceRadiusChange");
  act(() => change(radius));
}

const spot = (u: URL) => `${u.searchParams.get("lat")},${u.searchParams.get("lon")},${u.searchParams.get("radius")}`;
const panel = () => screen.getByRole("complementary", { name: "What happened here" });

function gated() {
  let release: () => void = () => {};
  const gate = new Promise<void>((resolve) => { release = resolve; });
  return { gate, release: () => release() };
}

describe("App moving the what-happened-here circle", () => {
  it("gives the map onPlaceMove only while the nearby panel is open", async () => {
    recordNearby();
    renderApp();
    expect(mapView.onPlaceMove).toBeNull();

    click(rome);
    await screen.findByRole("complementary", { name: "What happened here" });
    expect(mapView.onPlaceMove).toBeTypeOf("function");

    fireEvent.click(within(panel()).getByRole("button", { name: /close/i }));
    expect(mapView.onPlaceMove).toBeNull();
  });

  it("moves the centre and keeps the radius; the map's place follows immediately", async () => {
    recordNearby();
    await openNearby();
    dragTo(10000);

    moveTo({ lat: 48.85, lon: 2.35 });

    expect(mapView.place).toEqual({ lat: 48.85, lon: 2.35, radius: 10000 });
  });

  it("requests the moved centre with the same radius", async () => {
    const urls = recordNearby();
    await openNearby();
    await waitFor(() => expect(urls).toHaveLength(1));
    await sleep(300);

    moveTo({ lat: 48.85, lon: 2.35 });

    await waitFor(() => expect(urls.map(spot)).toContain("48.85,2.35,2293"));
  });

  it("moves across the antimeridian, from 179.9 to -179.8, keeping the radius", async () => {
    const urls = recordNearby();
    await openNearby(fiji);
    await sleep(300);

    moveTo({ lat: -17.8, lon: -179.8 });

    expect(mapView.place).toEqual({ lat: -17.8, lon: -179.8, radius: 12345 });
    await waitFor(() => expect(urls.map(spot)).toContain("-17.8,-179.8,12345"));
  });

  it("throttles searches during a move: first step at once, at most one per 250 ms, final position requested", async () => {
    const urls = recordNearby();
    await openNearby();
    await waitFor(() => expect(urls).toHaveLength(1));
    await sleep(300);
    const before = urls.length;

    moveTo({ lat: 42, lon: 12.5 });
    await waitFor(() => expect(urls.length).toBe(before + 1), { timeout: 100 });
    for (let i = 2; i <= 20; i++) {
      moveTo({ lat: 40 + i, lon: 12.5 });
      expect(mapView.place).toEqual({ lat: 40 + i, lon: 12.5, radius: 2293 });
      await sleep(20);
    }

    await waitFor(() => expect(urls.map(spot)).toContain("60,12.5,2293"));
    await sleep(400);
    const during = urls.slice(before);
    expect(during.length).toBeGreaterThanOrEqual(2);
    expect(during.length).toBeLessThan(8);
    expect(urls.map(spot).at(-1)).toBe("60,12.5,2293");
  });

  it("does not remount the panel or move focus while moving: the Search radius slider keeps focus", async () => {
    const urls = recordNearby();
    const opened = await openNearby();
    await waitFor(() => expect(urls).toHaveLength(1));
    const slider = within(opened).getByRole("slider", { name: "Search radius" });
    slider.focus();

    for (let i = 1; i <= 5; i++) {
      moveTo({ lat: 41.9 + i, lon: 12.5 });
      await sleep(60);
    }
    await sleep(300);

    expect(panel()).toBe(opened);
    expect(within(panel()).getByRole("slider", { name: "Search radius" })).toBe(slider);
    expect(slider).toHaveFocus();
  });

  it("keeps the previous list with Updating…, not Loading…, while a moved search loads", async () => {
    const { gate, release } = gated();
    recordNearby(async (url) => {
      if (latOf(url) === rome.lat) return HttpResponse.json({ events: [caesarDied], truncated: false });
      await gate;
      return HttpResponse.json({ events: [mozartBorn], truncated: false });
    });
    const opened = await openNearby();
    await within(opened).findByText("Julius Caesar");

    moveTo({ lat: 48.85, lon: 2.35 });

    expect(await within(opened).findByText("Updating…")).toBeInTheDocument();
    expect(within(opened).getByText("Julius Caesar")).toBeInTheDocument();
    expect(within(opened).queryByText("Loading…")).not.toBeInTheDocument();
    release();
    expect(await within(opened).findByRole("button", { name: /^Q254/ })).toBeInTheDocument();
    expect(within(opened).queryByText("Updating…")).not.toBeInTheDocument();
  });

  it("keeps the previous list with Updating…, not Loading…, while a resized search loads", async () => {
    const { gate, release } = gated();
    recordNearby(async (url) => {
      if (url.searchParams.get("radius") !== String(rome.radius)) await gate;
      return HttpResponse.json({ events: [caesarDied], truncated: false });
    });
    const opened = await openNearby();
    await within(opened).findByText("Julius Caesar");

    dragTo(9000);

    expect(await within(opened).findByText("Updating…")).toBeInTheDocument();
    expect(within(opened).getByText("Julius Caesar")).toBeInTheDocument();
    expect(within(opened).queryByText("Loading…")).not.toBeInTheDocument();
    release();
    await waitFor(() => expect(within(opened).queryByText("Updating…")).not.toBeInTheDocument());
  });

  it("shows the newest position's list when an older position's response arrives later", async () => {
    const urls = recordNearby(async (url) => {
      if (latOf(url) === 45) {
        await delay(300);
        return HttpResponse.json({ events: [caesarDied], truncated: false });
      }
      if (latOf(url) === 46) return HttpResponse.json({ events: [fijiFounded], truncated: false });
      return HttpResponse.json({ events: [mozartBorn], truncated: false });
    });
    const opened = await openNearby({ ...rome, lat: 44 });
    await within(opened).findByRole("button", { name: /^Q254/ });
    await sleep(300);

    moveTo({ lat: 45, lon: 12.5 });
    await waitFor(() => expect(urls.map(spot)).toContain("45,12.5,2293"));
    await sleep(260);
    moveTo({ lat: 46, lon: 12.5 });

    expect(await within(opened).findByRole("button", { name: /^Some village/ })).toBeInTheDocument();
    await sleep(400);
    expect(within(opened).getByRole("button", { name: /^Some village/ })).toBeInTheDocument();
    expect(within(opened).queryByRole("button", { name: /^Julius Caesar/ })).not.toBeInTheDocument();
  });

  it("shows a 400 problem's detail for a moved search inside the panel", async () => {
    recordNearby((url) =>
      latOf(url) === 89.99
        ? HttpResponse.json(
          { ...badBboxProblem, detail: "lat must be between -90 and 90", instance: "/places/nearby" },
          { status: 400, headers: problemJson },
        )
        : HttpResponse.json({ events: [caesarDied], truncated: false }));
    const opened = await openNearby();
    await sleep(300);

    moveTo({ lat: 89.99, lon: 12.5 });

    expect(await within(opened).findByRole("alert")).toHaveTextContent("lat must be between -90 and 90");
  });
});

describe("App a new map click after moving or resizing", () => {
  it.each([
    ["a move", () => moveTo({ lat: 45, lon: 12.5 })],
    ["a resize", () => dragTo(9000)],
  ])("searches the clicked spot at once and starts with Loading…, never the previous list, right after %s", async (_, change) => {
    const { gate, release } = gated();
    const urls = recordNearby(async (url) => {
      if (latOf(url) === fiji.lat) {
        await gate;
        return HttpResponse.json({ events: [fijiFounded], truncated: false });
      }
      return HttpResponse.json({ events: [caesarDied], truncated: false });
    });
    await openNearby();
    await within(panel()).findByText("Julius Caesar");
    await sleep(300);

    change();
    await sleep(30);
    click(fiji);

    await waitFor(() => expect(urls.map(spot)).toContain("-17.8,179.9,12345"), { timeout: 100 });
    expect(mapView.place).toEqual(fiji);
    expect(within(panel()).getByText("Loading…")).toBeInTheDocument();
    expect(within(panel()).queryByText("Julius Caesar")).not.toBeInTheDocument();
    expect(within(panel()).queryByText("Updating…")).not.toBeInTheDocument();
    release();
    expect(await within(panel()).findByText(/Some village/)).toBeInTheDocument();
  });
});
