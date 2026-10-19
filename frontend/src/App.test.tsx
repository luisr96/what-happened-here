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
  caesarDied,
  emptyEvents,
  eventsPage,
  fijiFounded,
  lisbonEarthquake,
  otherEventsPage,
  problemJson,
} from "./test/apiFixtures";
import { createQueryWrapper } from "./test/queryWrapper";
import { server } from "./test/server";

type MapViewProps = {
  readonly events: readonly EventView[];
  readonly onViewportChange: (bbox: Bbox) => void;
};

const mapView = vi.hoisted(() => ({ onViewportChange: null as ((bbox: Bbox) => void) | null }));

vi.mock("./map/MapView", () => ({
  MapView: ({ events, onViewportChange }: MapViewProps) => {
    mapView.onViewportChange = onViewportChange;
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
    expect(screen.getByRole("complementary", { name: "Filters and layers" })).toBeInTheDocument();
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

  it("sends minImportance when the slider moves", async () => {
    const urls = recordEvents();
    renderApp();
    reportViewport(europe);
    await waitFor(() => expect(urls).toHaveLength(1));

    fireEvent.change(screen.getByRole("slider", { name: "Covered in at least" }), { target: { value: "20" } });

    await waitFor(() => expect(urls.some((u) => u.searchParams.get("minImportance") === "20")).toBe(true));
    expect(screen.getByText("20 languages")).toBeInTheDocument();
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

  it("explains a truncated result in a status message", async () => {
    recordEvents(() => HttpResponse.json(otherEventsPage));
    renderApp();
    reportViewport(europe);

    const status = await screen.findByRole("status");
    expect(status).toHaveTextContent(TRUNCATED_HINT);
  });

  it("shows no truncation hint when the result is complete", async () => {
    recordEvents(() => HttpResponse.json(eventsPage));
    renderApp();
    reportViewport(europe);

    await mapEvents().findByText("Julius Caesar");
    expect(screen.queryByText(TRUNCATED_HINT)).not.toBeInTheDocument();
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

/** Extent 50 BC..2029. */
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
    expect(ranged?.searchParams.get("to")).toBe("2029");
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
