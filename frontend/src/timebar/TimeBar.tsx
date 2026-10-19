import { useMemo, useState, type ReactNode, type WheelEvent } from "react";
import type { components } from "../api/schema";
import { formatYear } from "../format/historicDate";
import { Histogram } from "./Histogram";
import { RangeTrack } from "./RangeTrack";
import { extentOf, ticks, yearFraction, zoom, type YearRange } from "./timeScale";
import { YearField } from "./YearField";

type DecadeCount = components["schemas"]["DecadeCount"];

export type TimeBarProps = {
  readonly decades: readonly DecadeCount[];
  readonly range: YearRange | null;
  readonly onRangeChange: (range: YearRange | null) => void;
};

const ZOOM_FACTOR = 2;
const WHEEL_ZOOM_FACTOR = 1.25;
const MAX_TICKS = 8;

/** Histogram, range handles, typed years and zoom. Holds only its zoom; App owns the range. */
export function TimeBar({ decades, range, onRangeChange }: TimeBarProps) {
  const extent = useMemo(() => extentOf(decades), [decades]);
  // null = the full extent, so an unzoomed bar follows the data as the viewport changes.
  const [zoomed, setZoomed] = useState<YearRange | null>(null);

  const view = extent === null ? null : within(zoomed ?? extent, extent);
  const atFullExtent = view === null || (view.from === extent?.from && view.to === extent.to);
  // With nothing selected the handles and boxes sit at the view's ends.
  const selected = range ?? view;

  /** Sets one end, keeping the other; ends that cross are swapped, so it's always earlier to later. */
  const setEnd = (end: "from" | "to", year: number) => {
    if (selected === null) return;
    const other = end === "from" ? selected.to : selected.from;
    onRangeChange({ from: Math.min(year, other), to: Math.max(year, other) });
  };

  const zoomBy = (factor: number, center?: number) => {
    if (view === null || extent === null) return;
    const middle = range === null ? (view.from + view.to) / 2 : (range.from + range.to) / 2;
    setZoomed(zoom(view, factor, center ?? middle, extent));
  };

  // The wheel zooms around the year under the cursor, the quickest way into a dense era.
  const zoomAtCursor = (e: WheelEvent<HTMLDivElement>) => {
    if (view === null || e.deltaY === 0) return;
    const box = e.currentTarget.getBoundingClientRect();
    const year = view.from + ((e.clientX - box.left) / box.width) * (view.to - view.from);
    zoomBy(e.deltaY < 0 ? 1 / WHEEL_ZOOM_FACTOR : WHEEL_ZOOM_FACTOR, year);
  };

  return (
    <section className="time-bar" aria-label="Timeline">
      <div className="time-bar__controls">
        <YearField label="From" year={selected?.from ?? null} onCommit={(year) => setEnd("from", year)} />
        <span aria-hidden="true">–</span>
        <YearField label="To" year={selected?.to ?? null} onCommit={(year) => setEnd("to", year)} />
        <button type="button" onClick={() => onRangeChange(null)} disabled={range === null}>
          All time
        </button>
        <span className="time-bar__spacer" />
        <IconButton label="Zoom in" onClick={() => zoomBy(1 / ZOOM_FACTOR)} disabled={view === null}>
          <path d="M8 3v10M3 8h10" />
        </IconButton>
        <IconButton label="Zoom out" onClick={() => zoomBy(ZOOM_FACTOR)} disabled={atFullExtent}>
          <path d="M3 8h10" />
        </IconButton>
        <IconButton label="Reset zoom" onClick={() => setZoomed(null)} disabled={atFullExtent}>
          <path d="M2 6V2h4M14 6V2h-4M2 10v4h4M14 10v4h-4" />
        </IconButton>
      </div>
      <div className="time-bar__chart" onWheel={zoomAtCursor}>
        <Histogram decades={decades} view={view} range={range} />
        {selected !== null && (
          <RangeTrack view={view} extent={extent} selected={selected} onChange={onRangeChange} />
        )}
        <div className="time-bar__ticks" aria-hidden="true">
          {view !== null && ticks(view, MAX_TICKS).map((year) => (
            <span key={year} style={{ left: `${yearFraction(view, year) * 100}%` }}>
              {formatYear(year)}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}

type IconButtonProps = {
  readonly label: string;
  readonly disabled: boolean;
  readonly onClick: () => void;
  readonly children: ReactNode;
};

/** A button showing only a 16×16 stroked icon; `label` is both its accessible name and tooltip. */
function IconButton({ label, disabled, onClick, children }: IconButtonProps) {
  return (
    <button type="button" className="icon-button" aria-label={label} title={label} disabled={disabled} onClick={onClick}>
      <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" focusable="false">
        <g fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">{children}</g>
      </svg>
    </button>
  );
}

/** A remembered zoom cut to the current extent; the whole extent once they no longer overlap. */
function within(view: YearRange, extent: YearRange): YearRange {
  const from = Math.max(view.from, extent.from);
  const to = Math.min(view.to, extent.to);
  return from < to ? { from, to } : extent;
}
