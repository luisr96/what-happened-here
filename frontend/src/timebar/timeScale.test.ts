import { describe, expect, it } from "vitest";
import type { components } from "../api/schema";
import { decadeYears, extentOf, skipYearZero, ticks, yearFraction, zoom } from "./timeScale";
import type { YearRange } from "./timeScale";

type DecadeCount = components["schemas"]["DecadeCount"];

const width = (r: YearRange) => r.to - r.from;
const extent: YearRange = { from: -7400, to: 2029 };

describe("decadeYears", () => {
  it("covers ten years for a CE decade", () => {
    expect(decadeYears(1880)).toEqual({ from: 1880, to: 1889 });
  });

  it("covers years 1..9 for decade 0, since year 0 does not exist", () => {
    expect(decadeYears(0)).toEqual({ from: 1, to: 9 });
  });

  it("covers -10..-1 for decade -10 (10 BC to 1 BC)", () => {
    expect(decadeYears(-10)).toEqual({ from: -10, to: -1 });
  });

  it("covers -50..-41 for decade -50, where 44 BC falls", () => {
    expect(decadeYears(-50)).toEqual({ from: -50, to: -41 });
  });
});

describe("extentOf", () => {
  it("is null for no decades", () => {
    expect(extentOf([])).toBeNull();
  });

  it("spans from the first year of the earliest decade to the last year of the latest, unsorted input", () => {
    const decades: DecadeCount[] = [
      { decade: 1880, count: 919 },
      { decade: -7400, count: 1 },
      { decade: 2020, count: 4 },
      { decade: 0, count: 2 },
    ];
    expect(extentOf(decades, 2029)).toEqual({ from: -7400, to: 2029 });
  });

  it("covers one decade alone", () => {
    expect(extentOf([{ decade: -10, count: 5 }], 2026)).toEqual({ from: -10, to: -1 });
  });

  it("ends the current decade at the current year: the 2020s end in 2026 during 2026", () => {
    const decades: DecadeCount[] = [{ decade: 1880, count: 3 }, { decade: 2020, count: 4 }];
    expect(extentOf(decades, 2026)).toEqual({ from: 1880, to: 2026 });
  });

  it("moves the end with the current year: 2027 during 2027", () => {
    const decades: DecadeCount[] = [{ decade: -50, count: 1 }, { decade: 2020, count: 4 }];
    expect(extentOf(decades, 2027)).toEqual({ from: -50, to: 2027 });
  });

  it("ignores the current year when the data ends in an earlier decade (1880s)", () => {
    const decades: DecadeCount[] = [{ decade: 1700, count: 1 }, { decade: 1880, count: 9 }];
    expect(extentOf(decades, 2026)).toEqual({ from: 1700, to: 1889 });
    expect(extentOf(decades, 2027)).toEqual({ from: 1700, to: 1889 });
  });

  it("still ends at the last decade's end if every decade starts after the current year", () => {
    expect(extentOf([{ decade: 2030, count: 1 }, { decade: 2040, count: 1 }], 2026)).toEqual({ from: 2030, to: 2049 });
  });

  it("defaults the current year to today's", () => {
    const now = new Date().getFullYear();
    const lastDecade = Math.floor(now / 10) * 10;
    expect(extentOf([{ decade: 1880, count: 1 }, { decade: lastDecade, count: 1 }])).toEqual({ from: 1880, to: now });
  });
});

describe("zoom", () => {
  it("halves the span around the centre for factor 0.5", () => {
    expect(zoom({ from: 1800, to: 2000 }, 0.5, 1900, extent)).toEqual({ from: 1850, to: 1950 });
  });

  it("keeps the centre at the same relative position", () => {
    expect(zoom({ from: 1800, to: 2000 }, 0.5, 1850, extent)).toEqual({ from: 1825, to: 1925 });
  });

  it("doubles the span for factor 2 and shifts back inside the extent", () => {
    expect(zoom({ from: 1900, to: 2029 }, 2, 2000, extent)).toEqual({ from: 1771, to: 2029 });
  });

  it("shifts back inside the extent at the BC end", () => {
    const result = zoom({ from: -7400, to: -7000 }, 2, -7300, extent);
    expect(result).toEqual({ from: -7400, to: -6600 });
  });

  it("clamps to the extent when zoomed out wider than it", () => {
    expect(zoom({ from: 1000, to: 1500 }, 100, 1250, extent)).toEqual(extent);
  });

  it("never gets narrower than 50 years", () => {
    const result = zoom({ from: 1800, to: 1900 }, 0.1, 1850, extent);
    expect(width(result)).toBe(50);
    expect(result.from).toBeLessThanOrEqual(1850);
    expect(result.to).toBeGreaterThanOrEqual(1850);
  });
});

describe("ticks", () => {
  it("picks round years: 1800..2020 with at most 6 gives every 50 years", () => {
    expect(ticks({ from: 1800, to: 2020 }, 6)).toEqual([1800, 1850, 1900, 1950, 2000]);
  });

  it("skips year 0, which does not exist", () => {
    const result = ticks({ from: -100, to: 100 }, 5);
    expect(result).not.toContain(0);
    expect(result).toContain(-100);
    expect(result).toContain(100);
  });

  it.each([
    [{ from: -7400, to: 2029 }, 8],
    [{ from: 1, to: 9 }, 5],
    [{ from: -44, to: 14 }, 6],
    [{ from: 1883, to: 1937 }, 4],
  ])("returns ascending nice years inside %o, at most %i", (view, max) => {
    const result = ticks(view, max);
    expect(result.length).toBeGreaterThan(0);
    expect(result.length).toBeLessThanOrEqual(max);
    expect([...result].sort((a, b) => a - b)).toEqual(result);
    expect(result).not.toContain(0);
    for (const t of result) {
      expect(t).toBeGreaterThanOrEqual(view.from);
      expect(t).toBeLessThanOrEqual(view.to);
    }
    if (result.length >= 2) {
      const steps = result.slice(1).map((t, i) => t - (result[i] ?? 0));
      const step = Math.min(...steps);
      const mantissa = step / 10 ** Math.floor(Math.log10(step));
      expect([1, 2, 5]).toContain(mantissa);
      for (const t of result) expect(Math.abs(t % step)).toBe(0);
    }
  });
});

describe("year 0 never escapes", () => {
  it("skipYearZero moves 0 one year in the given direction and leaves other years alone", () => {
    expect(skipYearZero(0, 1)).toBe(1);
    expect(skipYearZero(0, -1)).toBe(-1);
    expect(skipYearZero(-44, 1)).toBe(-44);
    expect(skipYearZero(14, -1)).toBe(14);
  });

  it("zoom never returns year 0 as an edge", () => {
    const extent = { from: -500, to: 500 };
    for (const center of [-50, -25, 0, 25, 50]) {
      const result = zoom(extent, 0.1, center, extent);
      expect([result.from, result.to]).not.toContain(0);
    }
  });
});

describe("yearFraction", () => {
  it("is 0 at the view's start, 1 at its end and linear between", () => {
    const view = { from: 1800, to: 2000 };
    expect(yearFraction(view, 1800)).toBe(0);
    expect(yearFraction(view, 2000)).toBe(1);
    expect(yearFraction(view, 1850)).toBe(0.25);
  });
});
