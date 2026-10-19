import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import type { components } from "../api/schema";
import {
  caesarDetails,
  fijiFounded,
  lisbonEarthquake,
  mozartDetailsNoWikipedia,
  problemJson,
} from "../test/apiFixtures";
import { createQueryWrapper } from "../test/queryWrapper";
import { server } from "../test/server";
import { DetailsPanel } from "./DetailsPanel";

type EventDetails = components["schemas"]["EventDetails"];
type ProblemDetail = components["schemas"]["ProblemDetail"];

/** Approximate (1-degree) point with a Wikipedia thumbnail. */
const lisbonDetails: EventDetails = {
  event: lisbonEarthquake,
  wikipedia: {
    title: "1755 Lisbon earthquake",
    extract: "The 1755 Lisbon earthquake struck Portugal on 1 November.",
    url: "https://en.wikipedia.org/wiki/1755_Lisbon_earthquake",
    thumbnailUrl: "https://upload.wikimedia.org/lisbon.jpg",
  },
};

/** Century-precision start and end near the antimeridian, precision unknown. */
const fijiDetails: EventDetails = {
  event: {
    ...fijiFounded,
    when: { year: 1100, month: 0, day: 0, precision: "CENTURY", calendar: "GREGORIAN" },
    end: { year: 1300, month: 0, day: 0, precision: "CENTURY", calendar: "GREGORIAN" },
  },
  wikipedia: null,
};

const notFound = (id: string): ProblemDetail => ({
  type: "about:blank",
  title: "Not Found",
  status: 404,
  detail: `No event ${id}`,
  instance: `/events/${id}`,
});

const byId: Readonly<Record<string, EventDetails>> = {
  [caesarDetails.event.id]: caesarDetails,
  [mozartDetailsNoWikipedia.event.id]: mozartDetailsNoWikipedia,
  [lisbonDetails.event.id]: lisbonDetails,
  [fijiDetails.event.id]: fijiDetails,
};

const CAESAR_EXTRACT = "Gaius Julius Caesar was a Roman general and statesman.";
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function serveDetails(delayFor: (id: string) => number = () => 0) {
  const ids: string[] = [];
  server.use(http.get("*/api/events/:id", async ({ params }) => {
    const id = decodeURIComponent(String(params["id"]));
    ids.push(id);
    const ms = delayFor(id);
    if (ms > 0) await delay(ms);
    const body = byId[id];
    return body
      ? HttpResponse.json(body)
      : HttpResponse.json(notFound(id), { status: 404, headers: problemJson });
  }));
  return ids;
}

function renderPanel(id: string) {
  const onClose = vi.fn();
  const utils = render(<DetailsPanel id={id} onClose={onClose} />, { wrapper: createQueryWrapper() });
  return { ...utils, onClose };
}

const panel = () => screen.getByRole("complementary", { name: "Event details" });
const title = (name: string) => screen.findByRole("heading", { level: 2, name });

describe("DetailsPanel", () => {
  it("is a complementary landmark named Event details", () => {
    serveDetails();
    renderPanel(caesarDetails.event.id);
    expect(panel()).toBeInTheDocument();
  });

  it("requests the event by id and shows Loading… in a status until it arrives", async () => {
    const ids = serveDetails(() => 100);
    renderPanel(caesarDetails.event.id);

    expect(within(panel()).getByRole("status")).toHaveTextContent("Loading…");
    expect(await title("Julius Caesar")).toBeInTheDocument();
    expect(screen.queryByText("Loading…")).not.toBeInTheDocument();
    expect(ids).toEqual(["Q1048:DIED"]);
  });

  it("shows the kind and a BC, Julian, day-precision date", async () => {
    serveDetails();
    renderPanel(caesarDetails.event.id);
    await title("Julius Caesar");
    expect(panel()).toHaveTextContent("Died 15 March 44 BC (Julian)");
  });

  it("does not call an exact location approximate", async () => {
    serveDetails();
    renderPanel(caesarDetails.event.id);
    await title("Julius Caesar");
    expect(screen.queryByText("Approximate location")).not.toBeInTheDocument();
  });

  it("shows the extract and a new-tab Wikipedia link, with no image when there is no thumbnail", async () => {
    serveDetails();
    renderPanel(caesarDetails.event.id);
    expect(await screen.findByText(CAESAR_EXTRACT)).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "Read on Wikipedia" });
    expect(link).toHaveAttribute("href", "https://en.wikipedia.org/wiki/Julius_Caesar");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel") ?? "").toContain("noopener");
    expect(panel().querySelector("img")).toBeNull();
    expect(screen.queryByText("No English Wikipedia article.")).not.toBeInTheDocument();
  });

  it("shows a thumbnail as a decorative image and flags an approximate location", async () => {
    serveDetails();
    renderPanel(lisbonDetails.event.id);
    await title("1755 Lisbon earthquake");
    const img = panel().querySelector("img");
    expect(img?.getAttribute("src")).toBe("https://upload.wikimedia.org/lisbon.jpg");
    expect(img?.getAttribute("alt")).toBe("");
    expect(screen.getByText("Approximate location")).toBeInTheDocument();
    expect(panel()).toHaveTextContent("Happened 1 November 1755");
  });

  it("falls back to the item id for an unlabelled event and says there is no Wikipedia article", async () => {
    serveDetails();
    renderPanel(mozartDetailsNoWikipedia.event.id);
    expect(await title("Q254")).toBeInTheDocument();
    expect(screen.getByText("No English Wikipedia article.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Read on Wikipedia" })).not.toBeInTheDocument();
    expect(panel()).toHaveTextContent("Born 27 January 1756");
  });

  it("shows a century range without inventing an exact date", async () => {
    serveDetails();
    renderPanel(fijiDetails.event.id);
    await title("Some village");
    expect(panel()).toHaveTextContent("Founded 11th century – 13th century");
    expect(screen.getByText("Approximate location")).toBeInTheDocument();
  });

  it("always links to the item on Wikidata in a new tab", async () => {
    serveDetails();
    renderPanel(mozartDetailsNoWikipedia.event.id);
    const link = await screen.findByRole("link", { name: "View on Wikidata" });
    expect(link).toHaveAttribute("href", "https://www.wikidata.org/wiki/Q254");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel") ?? "").toContain("noopener");
  });

  it("shows a 404 problem's detail in an alert", async () => {
    serveDetails();
    renderPanel("Q999:BORN");
    expect(await screen.findByRole("alert")).toHaveTextContent("No event Q999:BORN");
    expect(screen.queryByRole("link", { name: "View on Wikidata" })).not.toBeInTheDocument();
  });

  it("calls onClose from the Close details button", async () => {
    const user = userEvent.setup();
    serveDetails();
    const { onClose } = renderPanel(caesarDetails.event.id);
    await user.click(screen.getByRole("button", { name: "Close details" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("has no Back to list button without onBack", async () => {
    serveDetails();
    renderPanel(caesarDetails.event.id);
    await title("Julius Caesar");
    expect(screen.queryByRole("button", { name: "Back to list" })).not.toBeInTheDocument();
  });

  it("calls onBack, not onClose, from the Back to list button", async () => {
    const user = userEvent.setup();
    serveDetails();
    const onBack = vi.fn();
    const onClose = vi.fn();
    render(<DetailsPanel id={caesarDetails.event.id} onClose={onClose} onBack={onBack} />, { wrapper: createQueryWrapper() });
    await title("Julius Caesar");
    await user.click(within(panel()).getByRole("button", { name: "Back to list" }));
    expect(onBack).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
  });

  it("calls onClose on Escape while focus is inside the panel", async () => {
    const user = userEvent.setup();
    serveDetails();
    const { onClose } = renderPanel(caesarDetails.event.id);
    await title("Julius Caesar");
    screen.getByRole("button", { name: "Close details" }).focus();
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("ignores Escape while focus is outside the panel", async () => {
    const user = userEvent.setup();
    serveDetails();
    const onClose = vi.fn();
    render(
      <>
        <button type="button">Elsewhere</button>
        <DetailsPanel id={caesarDetails.event.id} onClose={onClose} />
      </>,
      { wrapper: createQueryWrapper() },
    );
    await title("Julius Caesar");
    screen.getByRole("button", { name: "Elsewhere" }).focus();
    await user.keyboard("{Escape}");
    expect(onClose).not.toHaveBeenCalled();
  });

  it("shows Loading… rather than the previous event while a new id loads, then the new event", async () => {
    serveDetails((id) => (id === mozartDetailsNoWikipedia.event.id ? 150 : 0));
    const onClose = vi.fn();
    const { rerender } = render(<DetailsPanel id={caesarDetails.event.id} onClose={onClose} />, {
      wrapper: createQueryWrapper(),
    });
    await title("Julius Caesar");

    rerender(<DetailsPanel id={mozartDetailsNoWikipedia.event.id} onClose={onClose} />);

    expect(screen.queryByRole("heading", { level: 2, name: "Julius Caesar" })).not.toBeInTheDocument();
    expect(within(panel()).getByRole("status")).toHaveTextContent("Loading…");
    expect(await title("Q254")).toBeInTheDocument();
    expect(screen.queryByText(CAESAR_EXTRACT)).not.toBeInTheDocument();
  });

  it("keeps showing the newest id when the older response arrives later", async () => {
    serveDetails((id) => (id === caesarDetails.event.id ? 200 : 0));
    const onClose = vi.fn();
    const { rerender } = render(<DetailsPanel id={caesarDetails.event.id} onClose={onClose} />, {
      wrapper: createQueryWrapper(),
    });
    rerender(<DetailsPanel id={lisbonDetails.event.id} onClose={onClose} />);

    await title("1755 Lisbon earthquake");
    await sleep(300);
    expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent("1755 Lisbon earthquake");
    expect(screen.queryByText(CAESAR_EXTRACT)).not.toBeInTheDocument();
  });
});

describe("DetailsPanel focus", () => {
  it("takes focus when it opens, so Escape works straight away", async () => {
    serveDetails();
    const { onClose } = renderPanel("Q1048:DIED");

    expect(panel()).toHaveFocus();
    await userEvent.setup().keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("gives focus back to what had it before opening", () => {
    serveDetails();
    const wrapper = createQueryWrapper();
    const { rerender } = render(<><button type="button">opener</button></>, { wrapper });
    screen.getByRole("button", { name: "opener" }).focus();

    rerender(<><button type="button">opener</button><DetailsPanel id="Q1048:DIED" onClose={() => {}} /></>);
    expect(panel()).toHaveFocus();
    rerender(<><button type="button">opener</button></>);

    expect(screen.getByRole("button", { name: "opener" })).toHaveFocus();
  });

  it("falls back to returnFocusTo when nothing focusable had focus (a click on the map)", () => {
    serveDetails();
    const wrapper = createQueryWrapper();
    const target = { current: null as HTMLElement | null };
    const Region = () => <section aria-label="Map" tabIndex={-1} ref={(el) => { target.current = el; }} />;
    const { rerender } = render(<Region />, { wrapper });

    rerender(<><Region /><DetailsPanel id="Q1048:DIED" onClose={() => {}} returnFocusTo={target} /></>);
    rerender(<Region />);

    expect(screen.getByRole("region", { name: "Map" })).toHaveFocus();
  });
});

describe("DetailsPanel external URLs", () => {
  it("makes no link or image from URLs that aren't https", async () => {
    const unsafe = {
      ...caesarDetails,
      wikipedia: { title: "x", extract: CAESAR_EXTRACT, url: "javascript:alert(1)", thumbnailUrl: "http://example.org/a.png" },
    };
    server.use(http.get("*/api/events/:id", () => HttpResponse.json(unsafe)));
    const { container } = renderPanel("Q1048:DIED");

    await screen.findByText(CAESAR_EXTRACT);
    expect(screen.queryByRole("link", { name: "Read on Wikipedia" })).not.toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByRole("link", { name: "View on Wikidata" })).toBeInTheDocument();
  });
});
