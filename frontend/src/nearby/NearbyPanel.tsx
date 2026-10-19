import { useEffect, useRef, type RefObject } from "react";
import { useNearby, type NearbyQuery } from "../api/hooks";
import { formatRange } from "../format/historicDate";
import { kindLabel } from "../map/kinds";
import { formatDistance, type Place } from "../map/nearby";
import { usePanelFocus } from "../util/usePanelFocus";

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
};

/** "What happened here": events around a clicked spot, most important first. Fetches its own list. */
export function NearbyPanel({ place, filters, focusId, onSelect, onClose, returnFocusTo }: NearbyPanelProps) {
  const nearby = useNearby(filters === null ? null : { ...place, ...filters });
  const panel = usePanelFocus<HTMLElement>(`${place.lat},${place.lon},${place.radius}`, returnFocusTo);
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
      className="details-panel"
      aria-label="What happened here"
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
    >
      <button type="button" className="details-panel__close" aria-label="Close" title="Close" onClick={onClose}>
        ×
      </button>
      <h2>What happened here</h2>
      <p className="details-panel__note">Within {formatDistance(place.radius)}</p>
      {filters === null && <p>Select at least one kind</p>}
      {filters !== null && nearby.isPending && <p role="status">Loading…</p>}
      {nearby.isError && <p role="alert" className="details-panel__error">{nearby.error.message}</p>}
      {nearby.isSuccess && nearby.data.events.length === 0 && <p>Nothing recorded here.</p>}
      {nearby.isSuccess && nearby.data.events.length > 0 && (
        <ul className="nearby-list">
          {nearby.data.events.map((event) => (
            <li key={event.id}>
              <button
                type="button"
                className="nearby-list__item"
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
