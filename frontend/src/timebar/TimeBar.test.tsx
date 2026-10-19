import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { components } from "../api/schema";
import { TimeBar } from "./TimeBar";
import type { YearRange } from "./timeScale";

type DecadeCount = components["schemas"]["DecadeCount"];

/** The current year: the 2020s decade ends there, not in 2029. */
const NOW = new Date().getFullYear();

/** Extent -50..NOW: a BC decade, decade 0 (years 1..9), a busy 1880s and the 2020s; unsorted. */
const decades: readonly DecadeCount[] = [
  { decade: 1880, count: 919 },
  { decade: -50, count: 3 },
  { decade: 0, count: 2 },
  { decade: 2020, count: 4 },
];

/** Extent 1000..1999, 999 years wide: on a 999 px track one pixel is one year. */
const millennium: readonly DecadeCount[] = [
  { decade: 1000, count: 5 },
  { decade: 1990, count: 7 },
];

const BAR_TITLE = /: \d+ events?$/;

function renderBar(range: YearRange | null = null, data: readonly DecadeCount[] = decades) {
  const onRangeChange = vi.fn<(range: YearRange | null) => void>();
  const view = render(<TimeBar decades={data} range={range} onRangeChange={onRangeChange} />);
  return { onRangeChange, ...view };
}

/** Holds the range like App does, so the bar sees its own updates. */
function Controlled({ initial, data = decades, onChange }: {
  readonly initial: YearRange | null;
  readonly data?: readonly DecadeCount[];
  readonly onChange: (r: YearRange | null) => void;
}) {
  const [range, setRange] = useState<YearRange | null>(initial);
  return (
    <TimeBar
      decades={data}
      range={range}
      onRangeChange={(r) => {
        onChange(r);
        setRange(r);
      }}
    />
  );
}

function renderControlled(initial: YearRange | null, data: readonly DecadeCount[] = decades) {
  const onChange = vi.fn<(r: YearRange | null) => void>();
  render(<Controlled initial={initial} data={data} onChange={onChange} />);
  return onChange;
}

/** Every element, the slider track included, measures as a track `width` px wide from x = 0. */
function stubTrackWidth(width: number) {
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue({
    x: 0, y: 0, left: 0, top: 0, right: width, bottom: 40, width, height: 40, toJSON: () => ({}),
  } as DOMRect);
}

const histogram = () => screen.getByRole("img", { name: "Events per decade" });
const bars = () => within(histogram()).queryAllByTitle(BAR_TITLE);
const fromSlider = () => screen.getByRole("slider", { name: "From" });
const toSlider = () => screen.getByRole("slider", { name: "To" });
// Decorative for assistive tech (hidden from it), so found by its tooltip.
const band = () => screen.getByTitle("Drag to move the range");
const num = (el: HTMLElement, attr: string) => Number(el.getAttribute(attr));
const sliderSpan = () => ({ min: num(fromSlider(), "aria-valuemin"), max: num(fromSlider(), "aria-valuemax") });
const fromYear = () => screen.getByRole("spinbutton", { name: "From year" });
const toYear = () => screen.getByRole("spinbutton", { name: "To year" });
const fromEra = () => screen.getByRole("button", { name: "From era" });
const toEra = () => screen.getByRole("button", { name: "To era" });

beforeEach(() => {
  // jsdom implements no pointer capture; a handle may still ask for it.
  for (const method of ["setPointerCapture", "releasePointerCapture"] as const) {
    if (!(method in Element.prototype)) {
      Object.defineProperty(Element.prototype, method, { configurable: true, value: () => undefined });
    }
  }
  if (!("hasPointerCapture" in Element.prototype)) {
    Object.defineProperty(Element.prototype, "hasPointerCapture", { configurable: true, value: () => false });
  }
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("TimeBar", () => {
  it("is a region named \"Timeline\"", () => {
    renderBar();

    expect(screen.getByRole("region", { name: "Timeline" })).toBeInTheDocument();
  });

  it("draws one bar per decade with its years and count, BC and decade 0 included", () => {
    renderBar();

    expect(bars()).toHaveLength(4);
    expect(within(histogram()).getByTitle("50 BC – 41 BC: 3 events")).toBeInTheDocument();
    expect(within(histogram()).getByTitle("1 – 9: 2 events")).toBeInTheDocument();
    expect(within(histogram()).getByTitle("1880 – 1889: 919 events")).toBeInTheDocument();
    expect(within(histogram()).getByTitle("2020 – 2029: 4 events")).toBeInTheDocument();
  });

  it("has no Play or Pause button", () => {
    renderBar({ from: 1800, to: 1900 });

    expect(screen.queryByRole("button", { name: /play|pause/i })).not.toBeInTheDocument();
  });

  it("\"All time\" clears the range", () => {
    const { onRangeChange } = renderBar({ from: 1800, to: 1900 });

    const button = screen.getByRole("button", { name: "All time" });
    expect(button).toBeEnabled();
    fireEvent.click(button);

    expect(onRangeChange).toHaveBeenLastCalledWith(null);
  });

  it("disables the \"All time\" button with no range", () => {
    renderBar();

    expect(screen.getByRole("button", { name: "All time" })).toBeDisabled();
  });

  it("draws no bars with no decades, and its handles change nothing", () => {
    const { onRangeChange } = renderBar(null, []);

    expect(bars()).toHaveLength(0);
    for (const slider of screen.queryAllByRole("slider")) {
      fireEvent.keyDown(slider, { key: "ArrowRight" });
      fireEvent.keyDown(slider, { key: "End" });
    }
    expect(onRangeChange).not.toHaveBeenCalled();
  });
});

describe("TimeBar handles", () => {
  it("are two focusable sliders named From and To, at the view's ends with no range", () => {
    renderBar();

    for (const slider of [fromSlider(), toSlider()]) {
      expect(slider).toHaveAttribute("tabindex", "0");
      expect(slider).toHaveAttribute("aria-valuemin", "-50");
      expect(slider).toHaveAttribute("aria-valuemax", String(NOW));
    }
    expect(fromSlider()).toHaveAttribute("aria-valuenow", "-50");
    expect(fromSlider()).toHaveAttribute("aria-valuetext", "50 BC");
    expect(toSlider()).toHaveAttribute("aria-valuenow", String(NOW));
    expect(toSlider()).toHaveAttribute("aria-valuetext", String(NOW));
    expect(screen.getAllByRole("slider")).toHaveLength(2);
  });

  it("show a BC selection with formatted years", () => {
    renderBar({ from: -44, to: 14 });

    expect(fromSlider()).toHaveAttribute("aria-valuenow", "-44");
    expect(fromSlider()).toHaveAttribute("aria-valuetext", "44 BC");
    expect(toSlider()).toHaveAttribute("aria-valuenow", "14");
    expect(toSlider()).toHaveAttribute("aria-valuetext", "14");
  });

  it("clamp their display to the view for a range reaching past it", () => {
    renderBar({ from: -3000, to: 5000 });

    expect(fromYear()).toHaveValue(3000);
    expect(fromEra()).toHaveTextContent("BC");
    expect(toYear()).toHaveValue(5000);
    expect(fromSlider()).toHaveAttribute("aria-valuenow", "-50");
    expect(toSlider()).toHaveAttribute("aria-valuenow", String(NOW));
  });

  it("ArrowRight and ArrowUp move From one year later, keeping the view's end as To", () => {
    const { onRangeChange } = renderBar();

    fireEvent.keyDown(fromSlider(), { key: "ArrowRight" });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -49, to: NOW });

    fireEvent.keyDown(fromSlider(), { key: "ArrowUp" });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -49, to: NOW });
  });

  it("ArrowLeft and ArrowDown move To one year earlier, keeping From", () => {
    const { onRangeChange } = renderBar({ from: -44, to: 1900 });

    fireEvent.keyDown(toSlider(), { key: "ArrowLeft" });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -44, to: 1899 });

    fireEvent.keyDown(toSlider(), { key: "ArrowDown" });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -44, to: 1899 });
  });

  it("skips year 0: 1 BC plus one year is AD 1, and AD 1 minus one is 1 BC", () => {
    const { onRangeChange } = renderBar({ from: -1, to: 1 });

    fireEvent.keyDown(fromSlider(), { key: "ArrowRight" });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1, to: 1 });

    fireEvent.keyDown(toSlider(), { key: "ArrowLeft" });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -1, to: -1 });
  });

  it("PageUp and PageDown move a tenth of the view's width", () => {
    const { onRangeChange } = renderBar({ from: 1500, to: 1800 }, millennium);

    fireEvent.keyDown(fromSlider(), { key: "PageUp" });
    const up = onRangeChange.mock.lastCall?.[0];
    expect(up?.to).toBe(1800);
    // A tenth of 999 years, however it is rounded.
    expect([1599, 1600]).toContain(up?.from);

    fireEvent.keyDown(toSlider(), { key: "PageDown" });
    const down = onRangeChange.mock.lastCall?.[0];
    expect(down?.from).toBe(1500);
    expect([1700, 1701]).toContain(down?.to);
  });

  it("Home and End move a handle to the view's ends", () => {
    const { onRangeChange } = renderBar({ from: 1500, to: 1800 }, millennium);

    fireEvent.keyDown(fromSlider(), { key: "Home" });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1000, to: 1800 });

    fireEvent.keyDown(toSlider(), { key: "End" });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1500, to: 1999 });
  });

  it("moving From past To swaps them; the moved handle keeps focus and is now named To", () => {
    const onChange = renderControlled({ from: 1900, to: 1900 });

    fromSlider().focus();
    fireEvent.keyDown(fromSlider(), { key: "ArrowRight" });

    expect(onChange).toHaveBeenLastCalledWith({ from: 1900, to: 1901 });
    expect(toSlider()).toHaveFocus();
    expect(toSlider()).toHaveAttribute("aria-valuenow", "1901");
    expect(fromSlider()).toHaveAttribute("aria-valuenow", "1900");
  });

  it("End on From with a range swaps the ends and leaves focus on the new To", () => {
    const onChange = renderControlled({ from: 1800, to: 1900 });

    fromSlider().focus();
    fireEvent.keyDown(fromSlider(), { key: "End" });

    expect(onChange).toHaveBeenLastCalledWith({ from: 1900, to: NOW });
    expect(toSlider()).toHaveFocus();
  });

  it("Home on To with a range swaps the ends and leaves focus on the new From", () => {
    const onChange = renderControlled({ from: 1800, to: 1900 });

    toSlider().focus();
    fireEvent.keyDown(toSlider(), { key: "Home" });

    expect(onChange).toHaveBeenLastCalledWith({ from: -50, to: 1800 });
    expect(fromSlider()).toHaveFocus();
  });

  it("dragging a handle sets its year from the pointer's position on the track", () => {
    stubTrackWidth(999);
    const { onRangeChange } = renderBar(null, millennium);

    fireEvent.pointerDown(fromSlider(), { pointerId: 1, clientX: 0, button: 0 });
    fireEvent.pointerMove(fromSlider(), { pointerId: 1, clientX: 500, button: 0 });

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1500, to: 1999 });
  });

  it("dragging To before From swaps the ends", () => {
    stubTrackWidth(999);
    const { onRangeChange } = renderBar({ from: 1600, to: 1800 }, millennium);

    fireEvent.pointerDown(toSlider(), { pointerId: 1, clientX: 800, button: 0 });
    fireEvent.pointerMove(toSlider(), { pointerId: 1, clientX: 200, button: 0 });

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1200, to: 1600 });
  });

  it("dragging a handle onto year 0 picks a real year", () => {
    stubTrackWidth(2079);
    const { onRangeChange } = renderBar({ from: -40, to: 1900 });

    fireEvent.pointerDown(fromSlider(), { pointerId: 1, clientX: 10, button: 0 });
    fireEvent.pointerMove(fromSlider(), { pointerId: 1, clientX: 50, button: 0 });

    const last = onRangeChange.mock.lastCall?.[0];
    expect(last?.to).toBe(1900);
    expect([-1, 1]).toContain(last?.from);
  });
});

describe("TimeBar selected band", () => {
  it("is not a slider", () => {
    renderBar({ from: 1200, to: 1300 }, millennium);

    expect(band()).toBeInTheDocument();
    expect(band()).not.toHaveAttribute("role", "slider");
  });

  it("dragging it shifts both ends by the same number of years", () => {
    stubTrackWidth(999);
    const { onRangeChange } = renderBar({ from: 1200, to: 1300 }, millennium);

    fireEvent.pointerDown(band(), { pointerId: 1, clientX: 250, button: 0 });
    fireEvent.pointerMove(band(), { pointerId: 1, clientX: 300, button: 0 });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1250, to: 1350 });

    fireEvent.pointerMove(band(), { pointerId: 1, clientX: 150, button: 0 });
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1100, to: 1200 });
  });

  it("stops at the extent's end, keeping the width", () => {
    stubTrackWidth(999);
    const { onRangeChange } = renderBar({ from: 1200, to: 1300 }, millennium);

    fireEvent.pointerDown(band(), { pointerId: 1, clientX: 250, button: 0 });
    fireEvent.pointerMove(band(), { pointerId: 1, clientX: 999, button: 0 });

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1899, to: 1999 });
  });

  it("stops at the extent's start, keeping the width", () => {
    stubTrackWidth(999);
    const { onRangeChange } = renderBar({ from: 1200, to: 1300 }, millennium);

    fireEvent.pointerDown(band(), { pointerId: 1, clientX: 250, button: 0 });
    fireEvent.pointerMove(band(), { pointerId: 1, clientX: -500, button: 0 });

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1000, to: 1100 });
  });

  it("never lands an end on year 0", () => {
    stubTrackWidth(2079);
    const { onRangeChange } = renderBar({ from: -20, to: -10 });

    fireEvent.pointerDown(band(), { pointerId: 1, clientX: 35, button: 0 });
    fireEvent.pointerMove(band(), { pointerId: 1, clientX: 45, button: 0 });

    const last = onRangeChange.mock.lastCall?.[0];
    expect(last).toBeDefined();
    expect(last?.from).not.toBe(0);
    expect(last?.to).not.toBe(0);
    expect(last?.from).toBe(-10);
  });

  it("stops following the pointer after pointerup", () => {
    stubTrackWidth(999);
    const { onRangeChange } = renderBar({ from: 1200, to: 1300 }, millennium);

    fireEvent.pointerDown(band(), { pointerId: 1, clientX: 250, button: 0 });
    fireEvent.pointerMove(band(), { pointerId: 1, clientX: 260, button: 0 });
    fireEvent.pointerUp(band(), { pointerId: 1, clientX: 260, button: 0 });
    const calls = onRangeChange.mock.calls.length;
    fireEvent.pointerMove(band(), { pointerId: 1, clientX: 400, button: 0 });

    expect(onRangeChange).toHaveBeenCalledTimes(calls);
  });
});

describe("TimeBar typed years", () => {
  it("show the view's ends with their era when no range is selected", () => {
    renderBar();

    expect(fromYear()).toHaveValue(50);
    expect(fromEra()).toHaveTextContent("BC");
    expect(toYear()).toHaveValue(NOW);
    expect(toEra()).toHaveTextContent("AD");
    expect(fromYear()).toHaveAttribute("min", "1");
  });

  it("show a BC range end as a positive year and BC", () => {
    renderBar({ from: -44, to: 14 });

    expect(fromYear()).toHaveValue(44);
    expect(fromEra()).toHaveTextContent("BC");
    expect(toYear()).toHaveValue(14);
    expect(toEra()).toHaveTextContent("AD");
  });

  it("commit a typed From year on Enter in its era", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: -44, to: 14 });

    await user.clear(fromYear());
    await user.type(fromYear(), "100{Enter}");

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -100, to: 14 });
  });

  it("commit a typed To year on blur", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: 1800, to: 1900 });

    await user.clear(toYear());
    await user.type(toYear(), "1950");
    expect(onRangeChange).not.toHaveBeenCalled();
    await user.tab();

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1800, to: 1950 });
  });

  it("allow years outside the timeline's extent", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar();

    await user.clear(fromYear());
    await user.type(fromYear(), "5000{Enter}");

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -5000, to: NOW });
  });

  it("swap the ends when the typed From is after To", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: 1800, to: 1900 });

    await user.clear(fromYear());
    await user.type(fromYear(), "1950{Enter}");

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1900, to: 1950 });
  });

  it.each(["0", ""])("mark %j as invalid, apply nothing and revert on blur", async (typed) => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: -44, to: 14 });

    await user.clear(fromYear());
    if (typed !== "") await user.type(fromYear(), typed);
    await user.keyboard("{Enter}");

    expect(fromYear()).toHaveAttribute("aria-invalid", "true");
    expect(onRangeChange).not.toHaveBeenCalled();

    await user.tab();
    expect(onRangeChange).not.toHaveBeenCalled();
    expect(fromYear()).toHaveValue(44);
    expect(fromYear()).not.toHaveAttribute("aria-invalid", "true");
  });

  it("flipping the To era applies immediately", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: -44, to: 14 });

    await user.click(toEra());

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -44, to: -14 });
  });

  it("flipping the From era to AD swaps ends that end up reversed", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: -44, to: 14 });

    await user.click(fromEra());

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 14, to: 44 });
  });

  it("flipping the From era with no range keeps the view's end as To", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar();

    await user.click(fromEra());

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 50, to: NOW });
  });

  it("a typed year with the BC era is negative", async () => {
    const user = userEvent.setup();
    const onChange = renderControlled({ from: 1800, to: 1900 });

    await user.click(fromEra());
    expect(onChange).toHaveBeenLastCalledWith({ from: -1800, to: 1900 });
    expect(fromEra()).toHaveTextContent("BC");

    await user.clear(fromYear());
    await user.type(fromYear(), "44{Enter}");
    expect(onChange).toHaveBeenLastCalledWith({ from: -44, to: 1900 });
  });
});

describe("TimeBar zoom", () => {
  it.each(["Zoom in", "Zoom out", "Reset zoom"])("%s is an icon button with a matching tooltip", (name) => {
    renderBar();

    const button = screen.getByRole("button", { name });
    expect(button).toHaveAttribute("title", name);
    expect(button.querySelector("svg")).not.toBeNull();
    expect(button.textContent?.trim()).toBe("");
  });

  it("zooming in narrows the handles' span and hides bars outside the view; Reset zoom restores it", () => {
    renderBar();
    expect(screen.getByRole("button", { name: "Zoom out" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Zoom in" }));

    const { min, max } = sliderSpan();
    expect(min).toBeGreaterThan(-50);
    expect(max).toBeLessThan(NOW);
    expect(max - min).toBeGreaterThanOrEqual(50);
    expect(within(histogram()).queryByTitle("50 BC – 41 BC: 3 events")).not.toBeInTheDocument();
    expect(within(histogram()).queryByTitle("2020 – 2029: 4 events")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Zoom out" })).toBeEnabled();

    fireEvent.click(screen.getByRole("button", { name: "Reset zoom" }));

    expect(sliderSpan()).toEqual({ min: -50, max: NOW });
    expect(bars()).toHaveLength(4);
    expect(screen.getByRole("button", { name: "Zoom out" })).toBeDisabled();
  });

  it("zooms around the selected range's middle", () => {
    renderBar({ from: 1880, to: 1889 });

    for (let i = 0; i < 4; i++) fireEvent.click(screen.getByRole("button", { name: "Zoom in" }));

    const { min, max } = sliderSpan();
    expect(max - min).toBeLessThan(2079 / 4);
    expect(min).toBeLessThanOrEqual(1880);
    expect(max).toBeGreaterThanOrEqual(1889);
    expect(within(histogram()).getByTitle("1880 – 1889: 919 events")).toBeInTheDocument();
  });

  it("zooming out after zooming in widens the view again", () => {
    renderBar();
    fireEvent.click(screen.getByRole("button", { name: "Zoom in" }));
    fireEvent.click(screen.getByRole("button", { name: "Zoom in" }));
    const narrow = sliderSpan();

    fireEvent.click(screen.getByRole("button", { name: "Zoom out" }));

    const wider = sliderSpan();
    expect(wider.max - wider.min).toBeGreaterThan(narrow.max - narrow.min);
  });

  it("the mouse wheel zooms in over the histogram", () => {
    stubTrackWidth(2079);
    renderBar();

    fireEvent.wheel(histogram(), { deltaY: -100, clientX: 1930 });

    const { min, max } = sliderSpan();
    expect(max - min).toBeLessThan(2079);
    expect(screen.getByRole("button", { name: "Zoom out" })).toBeEnabled();
  });

  it("a zoomed-in handle's keyboard steps keep the other end even when it lies outside the view", () => {
    const { onRangeChange } = renderBar({ from: -50, to: 2029 });
    for (let i = 0; i < 3; i++) fireEvent.click(screen.getByRole("button", { name: "Zoom in" }));
    const { min } = sliderSpan();

    fireEvent.keyDown(fromSlider(), { key: "Home" });

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: min, to: 2029 });
  });
});

describe("TimeBar typed years are at most 4 digits", () => {
  it("caps the boxes at 9999", () => {
    renderBar();

    expect(fromYear()).toHaveAttribute("max", "9999");
    expect(toYear()).toHaveAttribute("max", "9999");
  });

  it("ignores a typed 5th digit and shows nothing invalid", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: 1800, to: 1900 });

    await user.clear(toYear());
    await user.type(toYear(), "1950");
    await user.type(toYear(), "7");

    expect(toYear()).toHaveValue(1950);
    expect(toYear()).not.toHaveAttribute("aria-invalid", "true");
    await user.keyboard("{Enter}");
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: 1800, to: 1950 });
  });

  it("keeps the first 4 digits of a typed \"12345\"", async () => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: -44, to: 14 });

    await user.clear(fromYear());
    await user.type(fromYear(), "12345");

    expect(fromYear()).toHaveValue(1234);
    await user.keyboard("{Enter}");
    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -1234, to: 14 });
  });

  it("keeps the first 4 digits of a pasted \"12345\"", async () => {
    const user = userEvent.setup();
    renderBar({ from: 1800, to: 1900 });

    await user.clear(toYear());
    await user.click(toYear());
    await user.paste("12345");

    expect(toYear()).toHaveValue(1234);
    expect(toYear()).not.toHaveAttribute("aria-invalid", "true");
  });

  it.each([
    ["1", 1],
    ["9999", 9999],
  ])("commits the boundary year %s", async (typed, year) => {
    const user = userEvent.setup();
    const { onRangeChange } = renderBar({ from: -10000, to: 14 });

    await user.clear(toYear());
    await user.type(toYear(), `${typed}{Enter}`);

    expect(onRangeChange).toHaveBeenLastCalledWith({ from: -10000, to: year });
  });
});
