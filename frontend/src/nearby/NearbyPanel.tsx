import { useEffect, useRef, type CSSProperties, type RefObject } from "react";
import { useNearby, type NearbyQuery } from "../api/hooks";
import { formatRange } from "../format/historicDate";
import { kindLabel, kindStyle } from "../map/kinds";
import { clampRadius, formatDistance, MIN_SEARCH_RADIUS_M, type DistanceUnits, type Place } from "../map/nearby";
import { usePanelFocus } from "../util/usePanelFocus";
import { useThrottledValue } from "../util/useThrottledValue";

/** The map's current filters, sent with the search so the list matches what the map shows. */
export type NearbyFilters = Omit<NearbyQuery, "lat" | "lon" | "radius">;

export type NearbyPanelProps = {
  readonly place: Place;
  /** null when no kind is checked: the map shows nothing, so neither does the list. */
  readonly filters: NearbyFilters | null;
  /** An event to focus once the list shows (the one "Back to list" came from). */
  readonly focusId?: string;
  readonly onSelect: (id: string) => void;
  readonly onClose: () => void;
  readonly returnFocusTo?: RefObject<HTMLElement | null>;
  /** When given, a "Search radius" slider reports a new radius in metres for the same centre. */
  readonly onRadiusChange?: (metres: number) => void;
  /** How distances read; kilometres unless the settings say miles. */
  readonly units?: DistanceUnits;
};

// The slider is logarithmic: each step scales the radius by the same factor, so it is as fine
// at 200 m as at 200 km. Positions run 0..SLIDER_STEPS between these radii.
const SLIDER_MIN_M = MIN_SEARCH_RADIUS_M;
const SLIDER_MAX_M = 500000;
const SLIDER_STEPS = 1000;
const SEARCH_THROTTLE_MS = 250;

const toSlider = (metres: number) =>
  Math.round((Math.log(Math.max(metres, SLIDER_MIN_M) / SLIDER_MIN_M) / Math.log(SLIDER_MAX_M / SLIDER_MIN_M)) * SLIDER_STEPS);
const fromSlider = (position: number) =>
  Math.min(SLIDER_MAX_M, Math.max(SLIDER_MIN_M, clampRadius(SLIDER_MIN_M * (SLIDER_MAX_M / SLIDER_MIN_M) ** (position / SLIDER_STEPS))));

/** "What happened here": events around a clicked spot, most important first. Fetches its own list. */
export function NearbyPanel({
  place, filters, focusId, onSelect, onClose, returnFocusTo, onRadiusChange, units = "km",
}: NearbyPanelProps) {
  // Throttled so a moved or resized area updates the list as it goes, at most every 250 ms. A new
  // click mounts a new panel (App keys it per click), so a fresh spot is searched at once.
  const searched = useThrottledValue(place, SEARCH_THROTTLE_MS);
  const nearby = useNearby(filters === null ? null : { ...searched, ...filters });
  // Focus is taken once, on open: moving or resizing the area leaves it where it is (e.g. the
  // slider). A constant key works because App remounts this panel (its `key`) for each new click.
  const panel = usePanelFocus<HTMLElement>("nearby", returnFocusTo);
  const items = useRef(new Map<string, HTMLButtonElement>());

  // Back from an event's details lands on that event again, not at the top of a long list.
  const ready = nearby.isSuccess;
  useEffect(() => {
    if (ready && focusId !== undefined) items.current.get(focusId)?.focus();
  }, [ready, focusId]);

  return (
    <aside
      ref={panel}
      tabIndex={-1}
      className="details-panel panel"
      aria-label="What happened here"
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
    >
      <button type="button" className="details-panel__close" aria-label="Close" title="Close" onClick={onClose}>
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" focusable="false">
          <path d="M4 4l8 8M12 4l-8 8" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
      </button>
      <h2>What happened here</h2>
      <p className="details-panel__meta">Within {formatDistance(place.radius, units)}</p>
      {onRadiusChange !== undefined && (
        <input
          type="range"
          className="range nearby-radius"
          style={{ "--fill": String(toSlider(place.radius) / SLIDER_STEPS) } as CSSProperties}
          aria-label="Search radius"
          min={0}
          max={SLIDER_STEPS}
          step={1}
          value={toSlider(place.radius)}
          aria-valuetext={formatDistance(place.radius, units)}
          onChange={(e) => onRadiusChange(fromSlider(Number(e.target.value)))}
        />
      )}
      {filters === null && <p className="details-panel__empty">Select at least one kind</p>}
      {filters !== null && nearby.isPending && <p role="status" className="details-panel__note">Loading…</p>}
      {nearby.isPlaceholderData && <p role="status" className="details-panel__note">Updating…</p>}
      {nearby.isError && <p role="alert" className="details-panel__error">{nearby.error.message}</p>}
      {nearby.isSuccess && nearby.data.events.length === 0 && <p className="details-panel__empty">Nothing recorded here.</p>}
      {nearby.isSuccess && nearby.data.events.length > 0 && (
        <ul className="nearby-list">
          {nearby.data.events.map((event) => (
            <li key={event.id}>
              <button
                type="button"
                className="nearby-list__item"
                style={kindStyle(event.kind)}
                ref={(el) => {
                  if (el === null) items.current.delete(event.id);
                  else items.current.set(event.id, el);
                }}
                onClick={() => onSelect(event.id)}
              >
                <span className="nearby-list__label">{event.label ?? event.itemId}</span>
                <span className="nearby-list__when">
                  {`${kindLabel(event.kind)} ${formatRange(event.when, event.end)}`}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {nearby.isSuccess && nearby.data.truncated && (
        <p className="details-panel__note">Showing the most important events within this distance.</p>
      )}
    </aside>
  );
}
