import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { act } from "react";
import { describe, expect, it, vi } from "vitest";
import type { Bbox } from "./api/client";
import type { EventView } from "./api/hooks";
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

    fireEvent.change(screen.getByRole("slider", { name: "Minimum importance" }), { target: { value: "20" } });

    await waitFor(() => expect(urls.some((u) => u.searchParams.get("minImportance") === "20")).toBe(true));
    expect(screen.getByText("20 Wikipedias")).toBeInTheDocument();
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
