import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import type { components } from "../api/schema";
import {
  caesarDied,
  emptyEvents,
  fijiFounded,
  lisbonEarthquake,
  mozartBorn,
  problemJson,
} from "../test/apiFixtures";
import { createQueryWrapper } from "../test/queryWrapper";
import { server } from "../test/server";
import { NearbyPanel } from "./NearbyPanel";

type EventView = components["schemas"]["EventView"];
type EventsResponse = components["schemas"]["EventsResponse"];
type ProblemDetail = components["schemas"]["ProblemDetail"];
type Place = { lat: number; lon: number; radius: number };
type Filters = { from?: number; to?: number; kinds?: readonly EventView["kind"][]; minImportance?: number };

const rome: Place = { lat: 41.9, lon: 12.5, radius: 2293 };
const fiji: Place = { lat: -17.8, lon: 179.9, radius: 12345 };

/** A period with year precision on both ends that crosses from BC to AD. */
const augustusReign: EventView = {
  ...caesarDied,
  id: "Q1405:HAPPENED",
  itemId: "Q1405",
  kind: "HAPPENED",
  label: "Reign of Augustus",
  importance: 120,
  when: { year: -27, month: 0, day: 0, precision: "YEAR", calendar: "JULIAN" },
  end: { year: 14, month: 0, day: 0, precision: "YEAR", calendar: "JULIAN" },
  earliestYear: -27,
  latestYear: 14,
};

const romePage: EventsResponse = { events: [caesarDied, augustusReign, mozartBorn, lisbonEarthquake], truncated: false };
const TRUNCATED = "Showing the most important events within this distance.";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function serveNearby(respond: (url: URL) => Response | Promise<Response> = () => HttpResponse.json(romePage)) {
  const urls: URL[] = [];
  server.use(http.get("*/api/places/nearby", ({ request }) => {
    const url = new URL(request.url);
    urls.push(url);
    return respond(url);
  }));
  return urls;
}

function renderPanel(place: Place = rome, filters: Filters = {}) {
  const onSelect = vi.fn();
  const onClose = vi.fn();
  const utils = render(<NearbyPanel place={place} filters={filters} onSelect={onSelect} onClose={onClose} />, {
    wrapper: createQueryWrapper(),
  });
  return { ...utils, onSelect, onClose };
}

const panel = () => screen.getByRole("complementary", { name: "What happened here" });
const list = () => within(panel()).findByRole("list");
const items = async () => within(await list()).getAllByRole("button");

describe("NearbyPanel", () => {
  it("is a complementary landmark with a level-2 heading and the search distance", () => {
    serveNearby();
    renderPanel();
    expect(within(panel()).getByRole("heading", { level: 2, name: "What happened here" })).toBeInTheDocument();
    expect(panel()).toHaveTextContent("Within 2.3 km");
  });

  it("shows whole kilometres for a wider area", () => {
    serveNearby();
    renderPanel(fiji);
    expect(panel()).toHaveTextContent("Within 12 km");
  });

  it("requests lat, lon and radius for the place, near the antimeridian too", async () => {
    const urls = serveNearby(() => HttpResponse.json({ events: [fijiFounded], truncated: false }));
    renderPanel(fiji);
    await waitFor(() => expect(urls).toHaveLength(1));
    const q = urls[0]?.searchParams;
    expect(Number(q?.get("lat"))).toBe(-17.8);
    expect(Number(q?.get("lon"))).toBe(179.9);
    expect(q?.get("radius")).toBe("12345");
  });

  it("sends the given filters with the request, BC years negative", async () => {
    const urls = serveNearby();
    renderPanel(rome, { kinds: ["BORN"], from: -44, to: 14, minImportance: 20 });
    await waitFor(() => expect(urls).toHaveLength(1));
    const q = urls[0]?.searchParams;
    expect(q?.get("kinds")).toBe("BORN");
    expect(q?.get("from")).toBe("-44");
    expect(q?.get("to")).toBe("14");
    expect(q?.get("minImportance")).toBe("20");
  });

  it("sends no filter parameters when none are given", async () => {
    const urls = serveNearby();
    renderPanel();
    await waitFor(() => expect(urls).toHaveLength(1));
    const q = urls[0]?.searchParams;
    for (const name of ["kinds", "from", "to", "minImportance"]) expect(q?.has(name)).toBe(false);
  });

  it("shows Loading… in a status until the response arrives", async () => {
    serveNearby(async () => {
      await delay(100);
      return HttpResponse.json(romePage);
    });
    renderPanel();
    expect(within(panel()).getByRole("status")).toHaveTextContent("Loading…");
    await list();
    expect(screen.queryByText("Loading…")).not.toBeInTheDocument();
  });

  it("lists the events in API order as buttons named by label, itemId when unlabelled", async () => {
    serveNearby();
    renderPanel();
    const buttons = await items();
    expect(buttons).toHaveLength(4);
    expect(buttons[0]).toHaveAccessibleName(expect.stringMatching(/^Julius Caesar/));
    expect(buttons[1]).toHaveAccessibleName(expect.stringMatching(/^Reign of Augustus/));
    expect(buttons[2]).toHaveAccessibleName(expect.stringMatching(/^Q254/));
    expect(buttons[3]).toHaveAccessibleName(expect.stringMatching(/^1755 Lisbon earthquake/));
  });

  it("shows the kind and a BC, Julian, day-precision date", async () => {
    serveNearby();
    renderPanel();
    expect((await items())[0]).toHaveTextContent("Died 15 March 44 BC (Julian)");
  });

  it("shows a year-precision range crossing from BC to AD", async () => {
    serveNearby();
    renderPanel();
    expect((await items())[1]).toHaveTextContent("Happened 27 BC – 14");
  });

  it("shows a century-precision date as a century, never a fake exact year", async () => {
    serveNearby(() => HttpResponse.json({ events: [fijiFounded], truncated: false }));
    renderPanel(fiji);
    const [button] = await items();
    expect(button).toHaveTextContent("Founded 12th century");
    expect(button).not.toHaveTextContent("1200");
  });

  it("calls onSelect with the event id when an item is clicked", async () => {
    const user = userEvent.setup();
    serveNearby();
    const { onSelect } = renderPanel();
    const second = (await items())[1];
    if (second === undefined) throw new Error("no second item");
    await user.click(second);
    expect(onSelect).toHaveBeenCalledWith("Q1405:HAPPENED");
  });

  it("says nothing is recorded and shows no list for an empty result", async () => {
    serveNearby(() => HttpResponse.json(emptyEvents));
    renderPanel();
    expect(await within(panel()).findByText("Nothing recorded here.")).toBeInTheDocument();
    expect(within(panel()).queryByRole("list")).not.toBeInTheDocument();
  });

  it("explains a truncated result", async () => {
    serveNearby(() => HttpResponse.json({ events: [caesarDied], truncated: true }));
    renderPanel();
    expect(await within(panel()).findByText(TRUNCATED)).toBeInTheDocument();
  });

  it("does not show the truncation hint for a complete result", async () => {
    serveNearby();
    renderPanel();
    await list();
    expect(screen.queryByText(TRUNCATED)).not.toBeInTheDocument();
  });

  it("shows a 400 problem's detail in an alert", async () => {
    const problem: ProblemDetail = {
      type: "about:blank",
      title: "Bad Request",
      status: 400,
      detail: "radius must be between 1 and 500000",
      instance: "/places/nearby",
    };
    serveNearby(() => HttpResponse.json(problem, { status: 400, headers: problemJson }));
    renderPanel();
    expect(await within(panel()).findByRole("alert")).toHaveTextContent("radius must be between 1 and 500000");
  });

  it("shows the newest place's events when an older response arrives later", async () => {
    serveNearby(async (url) => {
      if (url.searchParams.get("radius") === "2293") {
        await delay(150);
        return HttpResponse.json(romePage);
      }
      return HttpResponse.json({ events: [fijiFounded], truncated: false });
    });
    const onSelect = vi.fn();
    const onClose = vi.fn();
    const { rerender } = render(<NearbyPanel place={rome} filters={{}} onSelect={onSelect} onClose={onClose} />, {
      wrapper: createQueryWrapper(),
    });
    rerender(<NearbyPanel place={fiji} filters={{}} onSelect={onSelect} onClose={onClose} />);

    expect(await within(panel()).findByText(/Some village/)).toBeInTheDocument();
    await sleep(250);
    expect(within(panel()).queryByText(/Julius Caesar/)).not.toBeInTheDocument();
    expect(panel()).toHaveTextContent("Within 12 km");
  });

  it("calls onClose from the Close button", async () => {
    const user = userEvent.setup();
    serveNearby();
    const { onClose } = renderPanel();
    await user.click(within(panel()).getByRole("button", { name: "Close" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("takes focus when it opens, so Escape closes it straight away", async () => {
    serveNearby();
    const { onClose } = renderPanel();
    expect(panel()).toHaveFocus();
    await userEvent.setup().keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("ignores Escape while focus is outside the panel", async () => {
    const user = userEvent.setup();
    serveNearby();
    const onClose = vi.fn();
    render(
      <>
        <button type="button">Elsewhere</button>
        <NearbyPanel place={rome} filters={{}} onSelect={() => {}} onClose={onClose} />
      </>,
      { wrapper: createQueryWrapper() },
    );
    screen.getByRole("button", { name: "Elsewhere" }).focus();
    await user.keyboard("{Escape}");
    expect(onClose).not.toHaveBeenCalled();
  });
});

describe("NearbyPanel review fixes", () => {
  it("with no kind checked (filters null) asks for one and sends no request", async () => {
    const urls = serveNearby();
    render(<NearbyPanel place={rome} filters={null} onSelect={vi.fn()} onClose={vi.fn()} />, {
      wrapper: createQueryWrapper(),
    });

    expect(within(panel()).getByText("Select at least one kind")).toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 50));
    expect(urls).toHaveLength(0);
    expect(within(panel()).queryByRole("status")).not.toBeInTheDocument();
  });

  it("focuses the item given by focusId once the list shows (back from its details)", async () => {
    serveNearby();
    render(<NearbyPanel place={rome} filters={{}} focusId={mozartBorn.id} onSelect={vi.fn()} onClose={vi.fn()} />, {
      wrapper: createQueryWrapper(),
    });

    const buttons = await items();
    const mozart = buttons.find((b) => b.textContent?.startsWith(mozartBorn.label ?? mozartBorn.itemId));
    await waitFor(() => expect(mozart).toHaveFocus());
  });
});
