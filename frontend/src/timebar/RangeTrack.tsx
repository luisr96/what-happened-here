import { useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { formatYear } from "../format/historicDate";
import { skipYearZero, yearFraction, type YearRange } from "./timeScale";

export type RangeTrackProps = {
  /** The years the track spans (the zoomed view); null while there is no data. */
  readonly view: YearRange | null;
  /** The data's extent: the band can't be dragged past it. */
  readonly extent: YearRange | null;
  /** The selected range, or the view's ends when nothing is selected; may reach outside the view. */
  readonly selected: YearRange;
  readonly onChange: (range: YearRange) => void;
};

type Handle = "a" | "b";

type Drag =
  | { readonly kind: "handle"; readonly handle: Handle; readonly anchor: number; lastYear: number }
  | { readonly kind: "band"; readonly startX: number; readonly start: YearRange };

const PAGE_FRACTION = 0.1;

/**
 * Two interchangeable handles and the band between them, laid over the histogram. Handles are
 * named by position, the left one "From": moving one past the other swaps their names, and the
 * range reported is always earlier year to later year.
 */
export function RangeTrack({ view, extent, selected, onChange }: RangeTrackProps) {
  const track = useRef<HTMLDivElement>(null);
  const drag = useRef<Drag | null>(null);
  // Which DOM handle is the left one. Flipped on a swap, so the handle being moved keeps focus
  // and pointer capture while its name changes from "From" to "To" (or back).
  const [aIsFrom, setAIsFrom] = useState(true);

  const isFrom = (handle: Handle) => (handle === "a") === aIsFrom;
  // A handle shows its end clamped to the view; moving it starts from there, while the other end
  // stays exactly as selected even when it lies outside the view.
  const yearOf = (handle: Handle) => {
    const year = isFrom(handle) ? selected.from : selected.to;
    return view === null ? year : Math.min(view.to, Math.max(view.from, year));
  };

  /** Moves `handle` to `year`, keeping the other end, and swaps names when it passes it. */
  const moveHandle = (handle: Handle, year: number, anchor: number, direction: 1 | -1) => {
    const moved = skipYearZero(year, direction);
    if (moved !== anchor) {
      const nowFrom = moved < anchor;
      if (nowFrom !== isFrom(handle)) setAIsFrom(handle === "a" ? nowFrom : !nowFrom);
    }
    onChange({ from: Math.min(moved, anchor), to: Math.max(moved, anchor) });
  };

  const yearAt = (clientX: number): number | null => {
    if (view === null || track.current === null) return null;
    const box = track.current.getBoundingClientRect();
    const fraction = Math.min(1, Math.max(0, (clientX - box.left) / box.width));
    return Math.round(view.from + fraction * (view.to - view.from));
  };

  const onHandleKey = (handle: Handle, e: KeyboardEvent<HTMLDivElement>) => {
    if (view === null) return;
    const year = yearOf(handle);
    const anchor = isFrom(handle) ? selected.to : selected.from;
    const page = Math.max(1, Math.round((view.to - view.from) * PAGE_FRACTION));
    const targets: Record<string, number> = {
      ArrowRight: year + 1,
      ArrowUp: year + 1,
      ArrowLeft: year - 1,
      ArrowDown: year - 1,
      PageUp: year + page,
      PageDown: year - page,
      Home: view.from,
      End: view.to,
    };
    const target = targets[e.key];
    if (target === undefined) return;
    e.preventDefault();
    moveHandle(handle, target, anchor, target >= year ? 1 : -1);
  };

  const startHandleDrag = (handle: Handle, e: PointerEvent<HTMLDivElement>) => {
    if (view === null) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    const year = yearOf(handle);
    drag.current = { kind: "handle", handle, anchor: isFrom(handle) ? selected.to : selected.from, lastYear: year };
  };

  const startBandDrag = (e: PointerEvent<HTMLDivElement>) => {
    if (view === null) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    drag.current = { kind: "band", startX: e.clientX, start: selected };
  };

  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    const current = drag.current;
    if (current === null || view === null || track.current === null) return;
    if (current.kind === "handle") {
      const year = yearAt(e.clientX);
      if (year === null) return;
      moveHandle(current.handle, year, current.anchor, year >= current.lastYear ? 1 : -1);
      current.lastYear = year;
      return;
    }
    // The band keeps its width: both ends shift by the pointer's distance in years, stopping
    // where either end would leave the data's extent.
    const width = track.current.getBoundingClientRect().width;
    let shift = Math.round(((e.clientX - current.startX) / width) * (view.to - view.from));
    if (extent !== null) {
      // A selection as wide as the data can't move within it.
      if (current.start.to - current.start.from >= extent.to - extent.from) return;
      shift = Math.min(shift, extent.to - current.start.to);
      shift = Math.max(shift, extent.from - current.start.from);
    }
    // An end landing on year 0 moves on to the next real year (the band is then a year narrower).
    const direction = shift >= 0 ? 1 : -1;
    onChange({
      from: skipYearZero(current.start.from + shift, direction),
      to: skipYearZero(current.start.to + shift, direction),
    });
  };

  const endDrag = () => {
    drag.current = null;
  };

  const position = (year: number) =>
    view === null ? 0 : Math.min(1, Math.max(0, yearFraction(view, year))) * 100;
  const left = position(selected.from);
  const right = position(selected.to);

  const handle = (id: Handle) => {
    const year = yearOf(id);
    return (
      <div
        key={id}
        className="range-track__handle"
        role="slider"
        tabIndex={0}
        aria-label={isFrom(id) ? "From" : "To"}
        aria-valuemin={view?.from}
        aria-valuemax={view?.to}
        aria-valuenow={year}
        aria-valuetext={formatYear(year)}
        aria-disabled={view === null}
        style={{ left: `${position(year)}%` }}
        onKeyDown={(e) => onHandleKey(id, e)}
        onPointerDown={(e) => startHandleDrag(id, e)}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
      />
    );
  };

  return (
    <div className="range-track" ref={track}>
      <div
        className="range-track__band"
        // A pointer shortcut only: keyboard and screen-reader users move the ends with the
        // handles or the typed years, so it stays out of the accessibility tree.
        aria-hidden="true"
        title="Drag to move the range"
        style={{ left: `${left}%`, width: `${right - left}%` }}
        onPointerDown={startBandDrag}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
      />
      {handle("a")}
      {handle("b")}
    </div>
  );
}
