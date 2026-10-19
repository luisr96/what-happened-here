import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { act } from "react";
import { describe, expect, it, vi } from "vitest";
import type { Bbox } from "./api/client";
import type { EventView, TimelineResponse } from "./api/hooks";
import { App } from "./App";
import {
  badBboxProblem,
  caesarDetails,
  caesarDied,
  emptyEvents,
  eventsPage,
  fijiFounded,
  lisbonEarthquake,
  mozartBorn,
  mozartDetailsNoWikipedia,
  otherEventsPage,
  problemJson,
} from "./test/apiFixtures";
import { createQueryWrapper } from "./test/queryWrapper";
import { server } from "./test/server";

type MapViewProps = {
  readonly events: readonly EventView[];
  readonly onViewportChange: (bbox: Bbox) => void;
  readonly selectedId?: string | null;
  readonly onSelect?: (id: string) => void;
  readonly onPlaceClick?: (place: Place) => void;
  readonly place?: Place | null;
};

type Place = { lat: number; lon: number; radius: number };

const mapView = vi.hoisted(() => ({
  onViewportChange: null as ((bbox: Bbox) => void) | null,
  onSelect: null as ((id: string) => void) | null,
  selectedId: undefined as string | null | undefined,
  onPlaceClick: null as ((place: { lat: number; lon: number; radius: number }) => void) | null,
  place: undefined as { lat: number; lon: number; radius: number } | null | undefined,
}));

vi.mock("./map/MapView", () => ({
  MapView: ({ events, onViewportChange, selectedId, onSelect, onPlaceClick, place }: MapViewProps) => {
    mapView.onPlaceClick = onPlaceClick ?? null;
    mapView.place = place;
    mapView.onViewportChange = onViewportChange;
    mapView.onSelect = onSelect ?? null;
    mapView.selectedId = selectedId;
    return (
      <section aria-label="Map">
        <ul aria-label="Events on the map">
          {(events ?? []).map((e) => <li key={e.id}>{e.label ?? e.itemId}</li>)}
        </ul>
      </section>
    );
  },
}));

const europe: Bbox = { minLon: -10, minLat: 35, maxLon: 30, maxLat: 60 };
const pacific: Bbox = { minLon: 170, minLat: -10, maxLon: -170, maxLat: 10 };
const TRUNCATED_HINT = "Showing the most important events here. Zoom in or raise the minimum importance to see more.";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function recordEvents(respond: (url: URL) => Response | Promise<Response> = () => HttpResponse.json(eventsPage)) {
  const urls: URL[] = [];
  server.use(http.get("*/api/events", ({ request }) => {
    const url = new URL(request.url);
    urls.push(url);
    return respond(url);
  }));
  return urls;
}

function renderApp() {
  mapView.onViewportChange = null;
  mapView.onSelect = null;
  mapView.selectedId = undefined;
  mapView.onPlaceClick = null;
  mapView.place = undefined;
  return render(<App />, { wrapper: createQueryWrapper() });
}

function reportViewport(bbox: Bbox) {
  const report = mapView.onViewportChange;
  if (report === null) throw new Error("MapView was not given onViewportChange");
  act(() => report(bbox));
}

function mapEvents() {
  return within(screen.getByRole("list", { name: "Events on the map" }));
}

describe("App", () => {
  it("lays out the map, the side panel and the timeline", () => {
    renderApp();

    expect(screen.getByRole("region", { name: "Map" })).toBeInTheDocument();
    expect(screen.getByRole("complementary", { name: "Filters" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Timeline" })).toBeInTheDocument();
  });
});

describe("App events on the map", () => {
  it("sends no request before the map reports a viewport", async () => {
    const urls = recordEvents();
    renderApp();
    await sleep(50);
    expect(urls).toHaveLength(0);
  });

  it("fetches events for the reported viewport with no kinds or minImportance filter by default", async () => {
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);

    await waitFor(() => expect(urls).toHaveLength(1));
    const params = urls[0]?.searchParams;
    expect(params?.get("bbox")).toBe("-10,35,30,60");
    expect(params?.has("kinds")).toBe(false);
    expect(params?.has("minImportance")).toBe(false);
  });

  it("sends an antimeridian-crossing bbox as given (minLon > maxLon)", async () => {
    const urls = recordEvents();
    renderApp();
    reportViewport(pacific);

    await waitFor(() => expect(urls).toHaveLength(1));
    expect(urls[0]?.searchParams.get("bbox")).toBe("170,-10,-170,10");
  });

  it("passes the response's events to the map, BC and approximate ones included", async () => {
    recordEvents();
    renderApp();
    reportViewport(europe);

    expect(await mapEvents().findByText("Julius Caesar")).toBeInTheDocument();
    expect(mapEvents().getByText("1755 Lisbon earthquake")).toBeInTheDocument();
  });

  it("shows an empty map for an empty result, without the truncation hint", async () => {
    const urls = recordEvents(() => HttpResponse.json(emptyEvents));
    renderApp();
    reportViewport(europe);

    await waitFor(() => expect(urls).toHaveLength(1));
    await waitFor(() => expect(mapEvents().queryAllByRole("listitem")).toHaveLength(0));
    expect(screen.queryByText(TRUNCATED_HINT)).not.toBeInTheDocument();
  });

  it("refetches without BORN when Born is unchecked", async () => {
    const user = userEvent.setup();
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);
    await waitFor(() => expect(urls).toHaveLength(1));

    await user.click(screen.getByRole("checkbox", { name: "Born" }));

    await waitFor(() => expect(urls).toHaveLength(2));
    expect(urls[1]?.searchParams.get("kinds")).toBe("HAPPENED,DIED,FOUNDED,ENDED,OPENED");
    expect(urls[1]?.searchParams.get("bbox")).toBe("-10,35,30,60");
  });

  it("sends no request and asks for a kind when every kind is unchecked", async () => {
    const user = userEvent.setup();
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);
    await waitFor(() => expect(urls).toHaveLength(1));

    for (const label of ["Happened", "Born", "Died", "Founded", "Ended"]) {
      await user.click(screen.getByRole("checkbox", { name: label }));
    }
    await waitFor(() => expect(urls.at(-1)?.searchParams.get("kinds")).toBe("OPENED"));
    const before = urls.length;

    await user.click(screen.getByRole("checkbox", { name: "Opened" }));

    expect(await screen.findByText("Select at least one kind")).toBeInTheDocument();
    await sleep(50);
    expect(urls).toHaveLength(before);
  });

  it("does not keep showing old events when every kind is unchecked", async () => {
    const user = userEvent.setup();
    recordEvents();
    renderApp();
    reportViewport(europe);
    expect(await mapEvents().findByText("Julius Caesar")).toBeInTheDocument();

    for (const label of ["Happened", "Born", "Died", "Founded", "Ended", "Opened"]) {
      await user.click(screen.getByRole("checkbox", { name: label }));
    }

    await screen.findByText("Select at least one kind");
    expect(mapEvents().queryByText("Julius Caesar")).not.toBeInTheDocument();
  });

  it.each([
    ["Notable", "50"],
    ["Famous", "100"],
  ])("requests minImportance right away when %s is chosen (no debounce)", async (name, value) => {
    const user = userEvent.setup();
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);
    await waitFor(() => expect(urls).toHaveLength(1));

    await user.click(screen.getByRole("radio", { name }));

    await waitFor(() => expect(urls.at(-1)?.searchParams.get("minImportance")).toBe(value), { timeout: 100 });
    expect(screen.getByRole("radio", { name })).toBeChecked();
  });

  it("goes back to unfiltered events when All is chosen again", async () => {
    const user = userEvent.setup();
    const urls = recordEvents((url) =>
      HttpResponse.json(url.searchParams.get("minImportance") === "100" ? emptyEvents : eventsPage));
    renderApp();
    reportViewport(europe);
    expect(await mapEvents().findByText("Julius Caesar")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Famous" }));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get("minImportance")).toBe("100"), { timeout: 100 });
    await waitFor(() => expect(mapEvents().queryByText("Julius Caesar")).not.toBeInTheDocument());

    await user.click(screen.getByRole("radio", { name: "All" }));

    expect(await mapEvents().findByText("Julius Caesar", {}, { timeout: 100 })).toBeInTheDocument();
    expect(urls.filter((u) => u.searchParams.has("minImportance")).every((u) => u.searchParams.get("minImportance") === "100")).toBe(true);
  });

  it("refetches when the viewport changes", async () => {
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);
    await waitFor(() => expect(urls).toHaveLength(1));

    reportViewport(pacific);

    await waitFor(() => expect(urls).toHaveLength(2));
    expect(urls[1]?.searchParams.get("bbox")).toBe("170,-10,-170,10");
  });

  it("shows the newest viewport's events when an older response arrives later", async () => {
    recordEvents(async (url) => {
      if (url.searchParams.get("bbox") === "-10,35,30,60") {
        await delay(150);
        return HttpResponse.json({ events: [caesarDied, lisbonEarthquake], truncated: false });
      }
      return HttpResponse.json({ events: [fijiFounded], truncated: false });
    });
    renderApp();
    reportViewport(europe);
    reportViewport(pacific);

    expect(await mapEvents().findByText("Some village")).toBeInTheDocument();
    await sleep(250);
    expect(mapEvents().getByText("Some village")).toBeInTheDocument();
    expect(mapEvents().queryByText("Julius Caesar")).not.toBeInTheDocument();
  });

  it("shows no notice for a truncated result (the user removed it)", async () => {
    recordEvents(() => HttpResponse.json(otherEventsPage));
    renderApp();
    reportViewport(europe);

    await mapEvents().findByText("Some village");
    expect(screen.queryByText(TRUNCATED_HINT)).not.toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("shows a 400 problem's detail in an alert", async () => {
    recordEvents(() => HttpResponse.json(badBboxProblem, { status: 400, headers: problemJson }));
    renderApp();
    reportViewport(europe);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("bbox must be minLon,minLat,maxLon,maxLat");
  });
});

function recordTimeline(body: TimelineResponse = appTimeline) {
  const urls: URL[] = [];
  server.use(http.get("*/api/stats/timeline", ({ request }) => {
    urls.push(new URL(request.url));
    return HttpResponse.json(body);
  }));
  return urls;
}

/** Extent 50 BC..the current year (the 2020s end now, not in 2029). */
const NOW = new Date().getFullYear();
const appTimeline: TimelineResponse = {
  decades: [
    { decade: -50, count: 3 },
    { decade: 1880, count: 919 },
    { decade: 2020, count: 4 },
  ],
};

const fromSlider = () => screen.getByRole("slider", { name: "From" });

async function timelineLoaded() {
  await waitFor(() => expect(fromSlider()).toHaveAttribute("aria-valuemin", "-50"));
}

describe("App timeline", () => {
  it("requests no timeline before the map reports a viewport", async () => {
    const timelineUrls = recordTimeline();
    recordEvents();
    renderApp();
    await sleep(50);
    expect(timelineUrls).toHaveLength(0);
  });

  it("requests the timeline for the viewport, antimeridian bbox as given, and draws its decades", async () => {
    const timelineUrls = recordTimeline();
    recordEvents();
    renderApp();
    reportViewport(pacific);

    await waitFor(() => expect(timelineUrls).toHaveLength(1));
    expect(timelineUrls[0]?.searchParams.get("bbox")).toBe("170,-10,-170,10");
    const histogram = screen.getByRole("img", { name: "Events per decade" });
    expect(await within(histogram).findByTitle("1880 – 1889: 919 events")).toBeInTheDocument();
    expect(within(histogram).getByTitle("50 BC – 41 BC: 3 events")).toBeInTheDocument();
  });

  it("sends neither from nor to while no time range is selected", async () => {
    recordTimeline();
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);

    await waitFor(() => expect(urls).toHaveLength(1));
    expect(urls[0]?.searchParams.has("from")).toBe(false);
    expect(urls[0]?.searchParams.has("to")).toBe(false);
  });

  it("requests the first range change immediately, then drops from and to for \"All time\"", async () => {
    recordTimeline();
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);
    await timelineLoaded();
    await waitFor(() => expect(urls).toHaveLength(1));

    fireEvent.keyDown(fromSlider(), { key: "ArrowRight" });

    await waitFor(() => expect(urls.some((u) => u.searchParams.get("from") === "-49")).toBe(true), { timeout: 150 });
    const ranged = urls.find((u) => u.searchParams.get("from") === "-49");
    expect(ranged?.searchParams.get("to")).toBe(String(NOW));
    expect(ranged?.searchParams.get("bbox")).toBe("-10,35,30,60");
    expect(screen.getByRole("spinbutton", { name: "From year" })).toHaveValue(49);
    expect(screen.getByRole("button", { name: "From era" })).toHaveTextContent("BC");

    await sleep(300);
    const before = urls.length;
    fireEvent.click(screen.getByRole("button", { name: "All time" }));

    await waitFor(() => expect(urls.length).toBeGreaterThan(before));
    await sleep(300);
    const last = urls.at(-1);
    expect(last?.searchParams.has("from")).toBe(false);
    expect(last?.searchParams.has("to")).toBe(false);
  });

  it("throttles a dragged range: the first and last ranges are requested, not every step", async () => {
    recordTimeline();
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);
    await timelineLoaded();
    await waitFor(() => expect(urls).toHaveLength(1));

    for (let i = 0; i < 20; i++) {
      fireEvent.keyDown(fromSlider(), { key: "ArrowRight" });
      await sleep(20);
    }

    await waitFor(() => expect(urls.some((u) => u.searchParams.get("from") === "-30")).toBe(true));
    await sleep(400);
    const froms = urls.map((u) => u.searchParams.get("from")).filter((f) => f !== null);
    expect(froms[0]).toBe("-49");
    expect(froms.at(-1)).toBe("-30");
    expect(froms.length).toBeLessThan(10);
  });

  it("sends a year typed into From year with its era", async () => {
    const user = userEvent.setup();
    recordTimeline();
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);
    await timelineLoaded();
    await waitFor(() => expect(urls).toHaveLength(1));

    const input = screen.getByRole("spinbutton", { name: "From year" });
    await user.clear(input);
    await user.type(input, "44{Enter}");

    await waitFor(() => expect(urls.some((u) => u.searchParams.get("from") === "-44")).toBe(true));
  });
});

function recordDetails() {
  const paths: string[] = [];
  server.use(http.get("*/api/events/:id", ({ request, params }) => {
    paths.push(new URL(request.url).pathname);
    const id = decodeURIComponent(String(params["id"]));
    const body = [caesarDetails, mozartDetailsNoWikipedia].find((d) => d.event.id === id);
    return body
      ? HttpResponse.json(body)
      : HttpResponse.json(
        { type: "about:blank", title: "Not Found", status: 404, detail: `No event ${id}`, instance: `/events/${id}` },
        { status: 404, headers: problemJson },
      );
  }));
  return paths;
}

function selectEvent(id: string) {
  const select = mapView.onSelect;
  if (select === null) throw new Error("MapView was not given onSelect");
  act(() => select(id));
}

const detailsPanel = () => screen.queryByRole("complementary", { name: "Event details" });

describe("App details panel", () => {
  it("shows no details panel and passes no selection before an event is clicked", () => {
    recordEvents();
    renderApp();
    expect(detailsPanel()).not.toBeInTheDocument();
    expect(mapView.selectedId ?? null).toBeNull();
  });

  it("opens the panel for a clicked event, requests its details and marks it selected on the map", async () => {
    recordEvents();
    const paths = recordDetails();
    renderApp();
    reportViewport(europe);
    await mapEvents().findByText("Julius Caesar");

    selectEvent(caesarDied.id);

    const panel = await screen.findByRole("complementary", { name: "Event details" });
    expect(await within(panel).findByRole("heading", { level: 2, name: "Julius Caesar" })).toBeInTheDocument();
    expect(mapView.selectedId).toBe("Q1048:DIED");
    expect(paths.map(decodeURIComponent)).toEqual(["/api/events/Q1048:DIED"]);
  });

  it("closes the panel and clears the selection with Close details", async () => {
    const user = userEvent.setup();
    recordEvents();
    recordDetails();
    renderApp();
    reportViewport(europe);
    selectEvent(caesarDied.id);
    await screen.findByRole("heading", { level: 2, name: "Julius Caesar" });

    await user.click(screen.getByRole("button", { name: "Close details" }));

    await waitFor(() => expect(detailsPanel()).not.toBeInTheDocument());
    expect(mapView.selectedId).toBeNull();
  });

  it("replaces the panel content when another event is selected", async () => {
    recordEvents();
    const paths = recordDetails();
    renderApp();
    reportViewport(europe);
    selectEvent(caesarDied.id);
    await screen.findByRole("heading", { level: 2, name: "Julius Caesar" });

    selectEvent(mozartDetailsNoWikipedia.event.id);

    expect(await screen.findByRole("heading", { level: 2, name: "Q254" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 2, name: "Julius Caesar" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("complementary", { name: "Event details" })).toHaveLength(1);
    expect(mapView.selectedId).toBe("Q254:BORN");
    expect(paths.map(decodeURIComponent)).toContain("/api/events/Q254:BORN");
  });

  it("shows a 404 problem for an unknown event inside the panel", async () => {
    recordEvents();
    recordDetails();
    renderApp();
    reportViewport(europe);

    selectEvent("Q999:BORN");

    const panel = await screen.findByRole("complementary", { name: "Event details" });
    expect(await within(panel).findByRole("alert")).toHaveTextContent("No event Q999:BORN");
  });
});

const rome: Place = { lat: 41.9, lon: 12.5, radius: 2293 };
const nearbyPage = { events: [caesarDied, mozartBorn], truncated: false };
const nearbyPanel = () => screen.queryByRole("complementary", { name: "What happened here" });

function recordNearby() {
  const urls: URL[] = [];
  server.use(http.get("*/api/places/nearby", ({ request }) => {
    urls.push(new URL(request.url));
    return HttpResponse.json(nearbyPage);
  }));
  return urls;
}

function clickPlace(place: Place) {
  const click = mapView.onPlaceClick;
  if (click === null) throw new Error("MapView was not given onPlaceClick");
  act(() => click(place));
}

async function openNearby() {
  recordEvents();
  recordDetails();
  const nearbyUrls = recordNearby();
  renderApp();
  reportViewport(europe);
  clickPlace(rome);
  const panel = await screen.findByRole("complementary", { name: "What happened here" });
  return { panel, nearbyUrls };
}

async function listItem(name: RegExp) {
  const panel = nearbyPanel();
  if (panel === null) throw new Error("nearby panel is not open");
  return within(await within(panel).findByRole("list")).findByRole("button", { name });
}

describe("App what happened here", () => {
  it("passes no place to the map before a click", () => {
    recordEvents();
    renderApp();
    expect(mapView.place ?? null).toBeNull();
    expect(nearbyPanel()).not.toBeInTheDocument();
  });

  it("opens the nearby panel for a clicked empty spot and draws the place on the map", async () => {
    const { panel, nearbyUrls } = await openNearby();

    expect(await within(panel).findByRole("button", { name: /^Julius Caesar/ })).toBeInTheDocument();
    expect(mapView.place).toEqual(rome);
    expect(mapView.selectedId ?? null).toBeNull();
    const q = nearbyUrls[0]?.searchParams;
    expect(Number(q?.get("lat"))).toBe(41.9);
    expect(Number(q?.get("lon"))).toBe(12.5);
    expect(q?.get("radius")).toBe("2293");
  });

  it("sends no filters with the nearby request by default", async () => {
    const { nearbyUrls } = await openNearby();
    await waitFor(() => expect(nearbyUrls).toHaveLength(1));
    const q = nearbyUrls[0]?.searchParams;
    for (const name of ["kinds", "minImportance", "from", "to"]) expect(q?.has(name)).toBe(false);
  });

  it("sends the current kinds, minImportance and time range with the nearby request", async () => {
    const user = userEvent.setup();
    recordTimeline();
    recordEvents();
    recordDetails();
    const nearbyUrls = recordNearby();
    renderApp();
    reportViewport(europe);
    await timelineLoaded();

    await user.click(screen.getByRole("checkbox", { name: "Born" }));
    await user.click(screen.getByRole("radio", { name: "Notable" }));
    fireEvent.keyDown(fromSlider(), { key: "ArrowRight" });
    await sleep(400);

    clickPlace(rome);
    await waitFor(() => expect(nearbyUrls.length).toBeGreaterThan(0));
    const q = nearbyUrls.at(-1)?.searchParams;
    expect(q?.get("kinds")).toBe("HAPPENED,DIED,FOUNDED,ENDED,OPENED");
    expect(q?.get("minImportance")).toBe("50");
    expect(q?.get("from")).toBe("-49");
    expect(q?.get("to")).toBe(String(NOW));
  });

  it("opens details from a list item with Back to list, keeping the place circle", async () => {
    const user = userEvent.setup();
    await openNearby();

    await user.click(await listItem(/^Julius Caesar/));

    const details = await screen.findByRole("complementary", { name: "Event details" });
    expect(await within(details).findByRole("heading", { level: 2, name: "Julius Caesar" })).toBeInTheDocument();
    expect(nearbyPanel()).not.toBeInTheDocument();
    expect(mapView.selectedId).toBe("Q1048:DIED");
    expect(mapView.place).toEqual(rome);
    expect(within(details).getByRole("button", { name: "Back to list" })).toBeInTheDocument();
  });

  it("returns to the same place's list with Back to list", async () => {
    const user = userEvent.setup();
    const { nearbyUrls } = await openNearby();
    await user.click(await listItem(/^Q254/));
    await screen.findByRole("heading", { level: 2, name: "Q254" });

    await user.click(screen.getByRole("button", { name: "Back to list" }));

    expect(await listItem(/^Julius Caesar/)).toBeInTheDocument();
    expect(detailsPanel()).not.toBeInTheDocument();
    expect(mapView.place).toEqual(rome);
    expect(mapView.selectedId ?? null).toBeNull();
    expect(nearbyUrls.every((u) => u.searchParams.get("radius") === "2293")).toBe(true);
  });

  it("replaces the nearby panel with details, without Back to list, when a point is clicked", async () => {
    await openNearby();
    await listItem(/^Julius Caesar/);

    selectEvent(caesarDied.id);

    const details = await screen.findByRole("complementary", { name: "Event details" });
    await within(details).findByRole("heading", { level: 2, name: "Julius Caesar" });
    expect(nearbyPanel()).not.toBeInTheDocument();
    expect(within(details).queryByRole("button", { name: "Back to list" })).not.toBeInTheDocument();
    expect(mapView.place ?? null).toBeNull();
    expect(mapView.selectedId).toBe("Q1048:DIED");
  });

  it("replaces open details with the nearby panel on an empty-spot click", async () => {
    recordEvents();
    recordDetails();
    recordNearby();
    renderApp();
    reportViewport(europe);
    selectEvent(caesarDied.id);
    await screen.findByRole("heading", { level: 2, name: "Julius Caesar" });

    clickPlace(rome);

    expect(await screen.findByRole("complementary", { name: "What happened here" })).toBeInTheDocument();
    expect(detailsPanel()).not.toBeInTheDocument();
    expect(mapView.selectedId ?? null).toBeNull();
    expect(mapView.place).toEqual(rome);
  });

  it("clears the place when the nearby panel is closed", async () => {
    const user = userEvent.setup();
    const { panel } = await openNearby();

    await user.click(within(panel).getByRole("button", { name: "Close" }));

    await waitFor(() => expect(nearbyPanel()).not.toBeInTheDocument());
    expect(mapView.place ?? null).toBeNull();
    expect(mapView.selectedId ?? null).toBeNull();
  });

  it("clears selection and place when details opened from the list are closed", async () => {
    const user = userEvent.setup();
    await openNearby();
    await user.click(await listItem(/^Julius Caesar/));
    await screen.findByRole("heading", { level: 2, name: "Julius Caesar" });

    await user.click(screen.getByRole("button", { name: "Close details" }));

    await waitFor(() => expect(detailsPanel()).not.toBeInTheDocument());
    expect(nearbyPanel()).not.toBeInTheDocument();
    expect(mapView.place ?? null).toBeNull();
    expect(mapView.selectedId ?? null).toBeNull();
  });
});
