import type { components } from "../api/schema";
import { formatYear } from "../format/historicDate";
import { decadeYears, yearFraction, type YearRange } from "./timeScale";

type DecadeCount = components["schemas"]["DecadeCount"];

export type HistogramProps = {
  readonly decades: readonly DecadeCount[];
  readonly view: YearRange | null;
  readonly range: YearRange | null;
};

const WIDTH = 1000;
const HEIGHT = 100;

/**
 * Bars per decade on a linear time axis spanning `view`. Heights use a square-root scale, since
 * recent decades hold hundreds of times more events than ancient ones.
 */
export function Histogram({ decades, view, range }: HistogramProps) {
  const visible = view === null ? [] : decades.filter((d) => overlaps(decadeYears(d.decade), view));
  const maxCount = Math.max(1, ...visible.map((d) => d.count));
  const x = (year: number) => (view === null ? 0 : yearFraction(view, year) * WIDTH);

  return (
    <svg
      className="histogram"
      role="img"
      aria-label="Events per decade"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
    >
      {visible.map((d) => {
        const years = decadeYears(d.decade);
        const height = Math.sqrt(d.count / maxCount) * HEIGHT;
        const selected = range === null || overlaps(years, range);
        const label = `${formatYear(years.from)} – ${formatYear(years.to)}: ${d.count} ${d.count === 1 ? "event" : "events"}`;
        // A nested <svg> per bar: its <title> is the hover text and, as an svg's title, the bar's name.
        return (
          <svg
            key={d.decade}
            className={selected ? "histogram__bar histogram__bar--selected" : "histogram__bar"}
            // From the decade's start, 0 included (decadeYears(0) starts at 1 only because year 0
            // doesn't exist), so every decade is drawn the same 10 years wide.
            x={x(d.decade)}
            width={Math.max(1, x(d.decade + 10) - x(d.decade) - 0.5)}
            y={HEIGHT - height}
            height={height}
          >
            <title>{label}</title>
            <rect width="100%" height="100%" />
          </svg>
        );
      })}
    </svg>
  );
}

function overlaps(a: YearRange, b: YearRange): boolean {
  return a.to >= b.from && a.from <= b.to;
}
