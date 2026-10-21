import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ABOUT_SEEN_KEY } from "./about/aboutSeen";
import { App } from "./App";
import { emptyEvents, problemJson, statsSummary } from "./test/apiFixtures";
import { createQueryWrapper } from "./test/queryWrapper";
import { server } from "./test/server";

vi.mock("./map/MapView", () => ({
  MapView: () => <section aria-label="Map" />,
}));

const DIALOG = "About the app";

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem(ABOUT_SEEN_KEY, "1");
  server.use(
    http.get("*/api/events", () => HttpResponse.json(emptyEvents)),
    http.get("*/api/stats/timeline", () => HttpResponse.json({ decades: [] })),
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

/** A total such as "5,720,295 events", whose number and unit are styled as separate pieces. */
const stat = (text: RegExp) => (_: string, el: Element | null) =>
  el?.tagName === "P" && text.test(el.textContent ?? "");

const renderApp = () => render(<App />, { wrapper: createQueryWrapper() });

async function openAbout() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "About" }));
  return { user, dialog: await screen.findByRole("dialog", { name: DIALOG }) };
}

describe("About dialog", () => {
  it("has an About button next to the Settings gear in the left panel", () => {
    renderApp();
    const side = screen.getByRole("complementary", { name: "Filters" });
    expect(within(side).getByRole("button", { name: "About" })).toBeInTheDocument();
    expect(within(side).getByRole("button", { name: "Settings" })).toBeInTheDocument();
  });

  it("does not open on load for a returning visitor", () => {
    renderApp();
    expect(screen.queryByRole("dialog", { name: DIALOG })).not.toBeInTheDocument();
  });

  it("opens a dialog that mentions Wikidata and moves focus into it", async () => {
    renderApp();
    const { dialog } = await openAbout();
    expect(dialog).toHaveTextContent(/Wikidata/);
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));
  });

  it("shows totals with thousands separators", async () => {
    renderApp();
    const { dialog } = await openAbout();
    expect(await within(dialog).findByText(stat(/5,720,295 events/))).toBeInTheDocument();
    expect(within(dialog).getByText(stat(/322,591 places/))).toBeInTheDocument();
  });

  it("shows events per kind with the kinds' labels", async () => {
    renderApp();
    const { dialog } = await openAbout();
    await within(dialog).findByText(stat(/5,720,295 events/));
    const expected: ReadonlyArray<readonly [string, string]> = [
      ["Happened", "192,249"],
      ["Born", "2,982,422"],
      ["Died", "1,287,177"],
      ["Founded", "952,643"],
      ["Ended", "157,511"],
      ["Opened", "148,293"],
    ];
    for (const [label, count] of expected) {
      const items = within(dialog)
        .getAllByRole("listitem")
        .filter((li) => new RegExp(`\\b${label}\\b`).test(li.textContent ?? ""));
      expect(items).toHaveLength(1);
      expect(items[0]).toHaveTextContent(count);
    }
  });

  it('shows the time span from the BC earliest year to "today", ignoring latestYear', async () => {
    renderApp();
    const { dialog } = await openAbout();
    expect(await within(dialog).findByText(stat(/10,000 BC to today/))).toBeInTheDocument();
    expect(dialog).not.toHaveTextContent(/2100/);
  });

  it("formats a CE earliest year without BC", async () => {
    server.use(http.get("*/api/stats/summary", () => HttpResponse.json({ ...statsSummary, earliestYear: 1066 })));
    renderApp();
    const { dialog } = await openAbout();
    expect(await within(dialog).findByText(stat(/1066 to today/))).toBeInTheDocument();
    expect(dialog).not.toHaveTextContent(/BC to today/);
  });

  it("names the Wikidata dump date in words", async () => {
    renderApp();
    const { dialog } = await openAbout();
    expect(await within(dialog).findByText(/Wikidata dump of 12 October 2026/)).toBeInTheDocument();
  });

  it("omits the dump date when dumpDate is null", async () => {
    server.use(http.get("*/api/stats/summary", () => HttpResponse.json({ ...statsSummary, dumpDate: null })));
    renderApp();
    const { dialog } = await openAbout();
    await within(dialog).findByText(stat(/5,720,295 events/));
    expect(dialog).not.toHaveTextContent(/dump of/);
    expect(dialog).not.toHaveTextContent(/null/);
  });

  it("shows a loading state while the numbers are on their way", async () => {
    server.use(http.get("*/api/stats/summary", () => new Promise<never>(() => {})));
    renderApp();
    const { dialog } = await openAbout();
    expect(within(dialog).getByText(/Loading/)).toBeInTheDocument();
    expect(dialog).not.toHaveTextContent(/5,720,295/);
  });

  it("keeps the text and says the numbers are unavailable when the request fails", async () => {
    server.use(
      http.get("*/api/stats/summary", () =>
        HttpResponse.json(
          { type: "about:blank", title: "Internal Server Error", status: 500, detail: "boom", instance: "/stats/summary" },
          { status: 500, headers: problemJson },
        ),
      ),
    );
    renderApp();
    const { dialog } = await openAbout();
    expect(await within(dialog).findByText(/Numbers unavailable/i)).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/Wikidata/);
    expect(dialog).not.toHaveTextContent(/\d,\d{3} events/);
  });

  it("survives a network error for the numbers", async () => {
    server.use(http.get("*/api/stats/summary", () => HttpResponse.error()));
    renderApp();
    const { dialog } = await openAbout();
    expect(await within(dialog).findByText(/Numbers unavailable/i)).toBeInTheDocument();
  });

  it("describes the stack and links to the GitHub repository in a new tab", async () => {
    renderApp();
    const { dialog } = await openAbout();
    expect(within(dialog).getByRole("heading", { name: /How it's built/i })).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/PostGIS/);
    expect(dialog).toHaveTextContent(/deck\.gl/);
    const link = within(dialog).getByRole("link", { name: /GitHub/ });
    expect(link).toHaveAttribute("href", "https://github.com/luisr96/what-happened-here");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel") ?? "").toMatch(/\bnoopener\b/);
  });

  it("closes with the Close button and returns focus to the About button", async () => {
    renderApp();
    const { user, dialog } = await openAbout();
    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: DIALOG })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "About" })).toHaveFocus();
  });

  it("closes with Escape and returns focus to the About button", async () => {
    renderApp();
    const { user } = await openAbout();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog", { name: DIALOG })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "About" })).toHaveFocus();
  });
});

describe("About dialog on a first visit", () => {
  it("opens automatically on the first load", async () => {
    localStorage.clear();
    renderApp();
    expect(await screen.findByRole("dialog", { name: DIALOG })).toBeInTheDocument();
  });

  it("does not open automatically again once dismissed", async () => {
    localStorage.clear();
    const user = userEvent.setup();
    const first = renderApp();
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    first.unmount();

    renderApp();

    expect(screen.getByRole("button", { name: "About" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: DIALOG })).not.toBeInTheDocument();
  });

  it("is remembered as dismissed after Escape too", async () => {
    localStorage.clear();
    const user = userEvent.setup();
    const first = renderApp();
    await screen.findByRole("dialog", { name: DIALOG });
    await user.keyboard("{Escape}");
    first.unmount();

    renderApp();

    expect(screen.getByRole("button", { name: "About" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: DIALOG })).not.toBeInTheDocument();
  });

  it("still opens on load and closes when localStorage throws", async () => {
    localStorage.clear();
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("SecurityError");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("QuotaExceededError");
    });
    const user = userEvent.setup();
    renderApp();
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: DIALOG })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "About" })).toBeInTheDocument();
  });
});
