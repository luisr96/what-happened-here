import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { caesarDied } from "../test/apiFixtures";
import { createQueryWrapper } from "../test/queryWrapper";
import { server } from "../test/server";
import { NearbyPanel } from "./NearbyPanel";

type Place = { lat: number; lon: number; radius: number };

const rome: Place = { lat: 41.9, lon: 12.5, radius: 2293 };

function renderPanel(place: Place = rome) {
  server.use(http.get("*/api/places/nearby", () => HttpResponse.json({ events: [caesarDied], truncated: false })));
  const onRadiusChange = vi.fn<(metres: number) => void>();
  const utils = render(
    <NearbyPanel place={place} filters={{}} onSelect={vi.fn()} onClose={vi.fn()} onRadiusChange={onRadiusChange} />,
    { wrapper: createQueryWrapper() },
  );
  return { ...utils, onRadiusChange };
}

const slider = () => screen.getByRole("slider", { name: "Search radius" });

function bounds(input: HTMLElement) {
  const min = Number(input.getAttribute("min") ?? "0");
  const max = Number(input.getAttribute("max") ?? "100");
  return { min, max };
}

function lastRadius(fn: ReturnType<typeof vi.fn>) {
  const call = fn.mock.calls.at(-1);
  if (call === undefined) throw new Error("onRadiusChange was not called");
  return Number(call[0]);
}

describe("NearbyPanel search radius", () => {
  it("has a range slider named Search radius whose value text is the formatted radius", () => {
    renderPanel();
    const input = slider();
    expect(input).toHaveAttribute("type", "range");
    expect(input).toHaveAttribute("aria-valuetext", "2.3 km");
  });

  it("shows metres below 1 km in the value text", () => {
    renderPanel({ ...rome, radius: 800 });
    expect(slider()).toHaveAttribute("aria-valuetext", "800 m");
  });

  it("calls onRadiusChange with 500 km at the slider's maximum", () => {
    const { onRadiusChange } = renderPanel();
    const input = slider();
    fireEvent.change(input, { target: { value: String(bounds(input).max) } });
    expect(lastRadius(onRadiusChange)).toBe(500000);
  });

  it("calls onRadiusChange with 100 m at the slider's minimum", () => {
    const { onRadiusChange } = renderPanel();
    const input = slider();
    fireEvent.change(input, { target: { value: String(bounds(input).min) } });
    expect(lastRadius(onRadiusChange)).toBe(100);
  });

  it("is logarithmic: the middle of the slider is the geometric mean (~7.1 km), not ~250 km", () => {
    const { onRadiusChange } = renderPanel();
    const input = slider();
    const { min, max } = bounds(input);
    fireEvent.change(input, { target: { value: String((min + max) / 2) } });
    const radius = lastRadius(onRadiusChange);
    expect(Math.abs(radius - Math.sqrt(100 * 500000)) / Math.sqrt(100 * 500000)).toBeLessThan(0.05);
  });

  it("sends whole metres within 100..500000 and a larger radius for a larger slider value", () => {
    const { onRadiusChange } = renderPanel();
    const input = slider();
    const { min, max } = bounds(input);
    fireEvent.change(input, { target: { value: String(min + (max - min) * 0.3) } });
    const smaller = lastRadius(onRadiusChange);
    fireEvent.change(input, { target: { value: String(min + (max - min) * 0.7) } });
    const larger = lastRadius(onRadiusChange);
    for (const r of [smaller, larger]) {
      expect(Number.isInteger(r)).toBe(true);
      expect(r).toBeGreaterThanOrEqual(100);
      expect(r).toBeLessThanOrEqual(500000);
    }
    expect(larger).toBeGreaterThan(smaller);
  });

  it("positions the slider for the current radius (round trip within 2%)", () => {
    renderPanel({ ...rome, radius: 12345 });
    const input = slider();
    const { min, max } = bounds(input);
    const fraction = (Number((input as HTMLInputElement).value) - min) / (max - min);
    const expected = Math.log(12345 / 100) / Math.log(500000 / 100);
    expect(Math.abs(fraction - expected)).toBeLessThan(0.02);
  });

  it("keeps the Within text following the radius", () => {
    const { rerender } = renderPanel();
    expect(screen.getByText("Within 2.3 km")).toBeInTheDocument();
    rerender(
      <NearbyPanel place={{ ...rome, radius: 50000 }} filters={{}} onSelect={vi.fn()} onClose={vi.fn()} onRadiusChange={vi.fn()} />,
    );
    expect(screen.getByText("Within 50 km")).toBeInTheDocument();
    expect(slider()).toHaveAttribute("aria-valuetext", "50 km");
  });
});

describe("NearbyPanel resize review fixes", () => {
  it("treats a new centre on the same panel as a move: keeps the list, says Updating… and searches the final centre", async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => { release = resolve; });
    const centres: string[] = [];
    server.use(http.get("*/api/places/nearby", async ({ request }) => {
      const url = new URL(request.url);
      centres.push(`${url.searchParams.get("lat")},${url.searchParams.get("lon")},${url.searchParams.get("radius")}`);
      if (centres.length > 1) await gate;
      return HttpResponse.json({ events: [caesarDied], truncated: false });
    }));
    const props = { filters: {}, onSelect: vi.fn(), onClose: vi.fn(), onRadiusChange: vi.fn() };
    const { rerender } = render(<NearbyPanel place={rome} {...props} />, { wrapper: createQueryWrapper() });
    await screen.findByText("Julius Caesar");

    const fiji: Place = { lat: -17.8, lon: 179.9, radius: 2293 };
    rerender(<NearbyPanel place={fiji} {...props} />);

    expect(await screen.findByText("Updating…")).toBeInTheDocument();
    expect(screen.getByText("Julius Caesar")).toBeInTheDocument();
    expect(screen.queryByText("Loading…")).not.toBeInTheDocument();
    await waitFor(() => expect(centres).toContain("-17.8,179.9,2293"));
    release();
    await waitFor(() => expect(screen.queryByText("Updating…")).not.toBeInTheDocument());
  });

  it("says Updating… while a resized search loads, keeping the previous list", async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => { release = resolve; });
    let calls = 0;
    server.use(http.get("*/api/places/nearby", async () => {
      calls++;
      if (calls > 1) await gate;
      return HttpResponse.json({ events: [caesarDied], truncated: false });
    }));
    const props = { filters: {}, onSelect: vi.fn(), onClose: vi.fn(), onRadiusChange: vi.fn() };
    const { rerender } = render(<NearbyPanel place={rome} {...props} />, { wrapper: createQueryWrapper() });
    await screen.findByText("Julius Caesar");

    rerender(<NearbyPanel place={{ ...rome, radius: 9000 }} {...props} />);

    expect(await screen.findByText("Updating…")).toBeInTheDocument();
    expect(screen.getByText("Julius Caesar")).toBeInTheDocument();
    release();
    await waitFor(() => expect(screen.queryByText("Updating…")).not.toBeInTheDocument());
  });
});
