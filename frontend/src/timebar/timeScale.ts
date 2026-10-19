import type { components } from "../api/schema";

type DecadeCount = components["schemas"]["DecadeCount"];

/** Inclusive span of years in historical numbering (negative = BC, no year 0). */
export type YearRange = { readonly from: number; readonly to: number };

const MIN_VIEW_YEARS = 50;

/**
 * The years a /stats/timeline decade covers: `decade` is floor(year / 10) * 10, so -10 is
 * 10 BC..1 BC and 0 is 1..9 (there is no year 0).
 */
/**
 * There is no year 0 (1 BC is followed by AD 1), but arithmetic on years and native sliders
 * produce it; this moves it one year in `direction` (+1 to AD 1, -1 to 1 BC).
 */
export function skipYearZero(year: number, direction: 1 | -1): number {
  return year === 0 ? direction : year;
}

/** Where `year` sits across `view`, 0 at view.from and 1 at view.to (bars, ticks and sliders). */
export function yearFraction(view: YearRange, year: number): number {
  return view.to === view.from ? 0 : (year - view.from) / (view.to - view.from);
}

export function decadeYears(decade: number): YearRange {
  return { from: decade === 0 ? 1 : decade, to: decade + 9 };
}

export function extentOf(decades: readonly DecadeCount[]): YearRange | null {
  if (decades.length === 0) return null;
  const starts = decades.map((d) => d.decade);
  return { from: decadeYears(Math.min(...starts)).from, to: decadeYears(Math.max(...starts)).to };
}

/**
 * Scales `view` by `factor` (< 1 zooms in) so that `center` keeps its relative position, then
 * keeps it inside `extent` (shifting it back, or clamping when wider) and at least 50 years wide.
 */
export function zoom(view: YearRange, factor: number, center: number, extent: YearRange): YearRange {
  const extentWidth = extent.to - extent.from;
  const width = Math.min(extentWidth, Math.max(MIN_VIEW_YEARS, (view.to - view.from) * factor));
  if (width >= extentWidth) return extent;
  const position = view.to === view.from ? 0.5 : (center - view.from) / (view.to - view.from);
  let from = Math.round(center - position * width);
  from = Math.max(extent.from, Math.min(from, extent.to - Math.round(width)));
  // Widened past year 0 rather than narrowed, so the view still contains `center`.
  return { from: skipYearZero(from, -1), to: skipYearZero(from + Math.round(width), 1) };
}

/** Round years (steps of 1, 2 or 5 × 10^k) inside `view`, at most `maxTicks`; never year 0. */
export function ticks(view: YearRange, maxTicks: number): number[] {
  for (let power = 1; ; power *= 10) {
    for (const step of [power, 2 * power, 5 * power]) {
      const years: number[] = [];
      for (let year = Math.ceil(view.from / step) * step; year <= view.to; year += step) {
        if (year !== 0) years.push(year);
      }
      if (years.length <= maxTicks) return years;
    }
  }
}
