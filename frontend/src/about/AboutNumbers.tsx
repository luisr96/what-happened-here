import type { CSSProperties } from "react";
import type { UseQueryResult } from "@tanstack/react-query";
import type { ApiError } from "../api/client";
import type { StatsSummary } from "../api/hooks";
import { KINDS, kindLabel, kindStyle } from "../map/kinds";

const NUMBER = new Intl.NumberFormat("en-US");
const PERCENT = new Intl.NumberFormat("en-US", {
  style: "percent", minimumFractionDigits: 1, maximumFractionDigits: 1,
});

export type AboutNumbersProps = {
  readonly stats: UseQueryResult<StatsSummary, ApiError>;
};

/** The dataset in numbers, or a short note while they load or when they can't be had. */
export function AboutNumbers({ stats }: AboutNumbersProps) {
  if (stats.isError) {
    return <p className="about__numbers about__numbers--state">Numbers unavailable right now.</p>;
  }
  if (stats.data === undefined) {
    return <p className="about__numbers about__numbers--state">Loading…</p>;
  }
  const { events, places, eventsByKind, earliestYear, dumpDate } = stats.data;
  // Largest first, so the bar reads left to right from the biggest slice and the legend follows it.
  const kinds = KINDS
    .map((kind) => ({ kind, count: eventsByKind[kind] ?? 0 }))
    .sort((a, b) => b.count - a.count);
  // Shares of the kinds' own sum, so the slices always add up to the whole bar.
  const total = Math.max(1, kinds.reduce((sum, { count }) => sum + count, 0));
  const spanEnd = dumpDate === null ? "today" : shortDate(dumpDate);
  return (
    <section className="about__numbers" aria-label="The data in numbers">
      <div className="about__totals">
        <p className="about__stat">
          <strong>{NUMBER.format(events)}</strong> events
        </p>
        <p className="about__stat">
          <strong>{NUMBER.format(places)}</strong> places
        </p>
        {earliestYear !== null && (
          <p className="about__stat">
            <strong>{spanStart(earliestYear)}</strong> to {spanEnd}
          </p>
        )}
      </div>
      {/* Hidden from screen readers: the legend below carries the same figures. */}
      <div className="about__share" aria-hidden="true">
        {kinds.map(({ kind, count }) => (
          <span
            key={kind}
            className="about__slice"
            style={{ ...kindStyle(kind), flexGrow: count } as CSSProperties}
          />
        ))}
      </div>
      <ul className="about__kinds">
        {kinds.map(({ kind, count }) => (
          <li key={kind} className="about__kind" style={kindStyle(kind)}>
            <span className="kinds__swatch" aria-hidden="true" />
            <span className="about__kind-label">{kindLabel(kind)}</span>{" "}
            <span className="about__value">
              <span className="about__count">{NUMBER.format(count)}</span>{" "}
              <span className="about__percent">{PERCENT.format(count / total)}</span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

// latestYear isn't used: a 21st-century date at century precision makes it 2100.
function spanStart(year: number): string {
  return year < 0 ? `${NUMBER.format(-year)} BC` : String(year);
}

/** "2026-10-12" → "Oct 12, 2026"; a plain calendar date, so read in UTC to avoid shifting. */
function shortDate(date: string): string {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "short", day: "numeric", year: "numeric", timeZone: "UTC",
  });
}
