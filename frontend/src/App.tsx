import { useRef, useState } from "react";
import type { Bbox, Kind } from "./api/client";
import { useEvent, useEvents, useTimeline } from "./api/hooks";
import { DetailsPanel } from "./details/DetailsPanel";
import { MapNotices } from "./map/MapNotices";
import { MapView } from "./map/MapView";
import { KINDS } from "./map/kinds";
import type { Place } from "./map/nearby";
import { NearbyPanel, type NearbyFilters } from "./nearby/NearbyPanel";
import { SidePanel } from "./panel/SidePanel";
import { TimeBar } from "./timebar/TimeBar";
import type { YearRange } from "./timebar/timeScale";
import { useDebouncedValue } from "./util/useDebouncedValue";
import { useThrottledValue } from "./util/useThrottledValue";

// The importance slider only matters where it stops, so it's debounced. The time range is
// throttled instead: the map keeps following while the range is dragged.
const SLIDER_SETTLE_MS = 250;
const RANGE_THROTTLE_MS = 250;

/**
 * The one panel on the right, if any. Details opened from the nearby list remember its place, so
 * "Back to list" can return to it and the searched area stays drawn meanwhile.
 */
type RightPanel =
  | { readonly kind: "details"; readonly id: string; readonly fromPlace: Place | null }
  | { readonly kind: "nearby"; readonly place: Place; readonly focusId?: string }
  | null;

export function App() {
  const [bbox, setBbox] = useState<Bbox | null>(null);
  const [kinds, setKinds] = useState<readonly Kind[]>(KINDS);
  const [minImportance, setMinImportance] = useState(0);
  const [range, setRange] = useState<YearRange | null>(null);
  const [panel, setPanel] = useState<RightPanel>(null);
  const mapRef = useRef<HTMLElement>(null);

  const requestedMinImportance = useDebouncedValue(minImportance, SLIDER_SETTLE_MS);
  const requestedRange = useThrottledValue(range, RANGE_THROTTLE_MS);

  const noKinds = kinds.length === 0;
  // Shared by the map and "what happened here", so the list matches the map. Defaults are left
  // out of the request, so the API applies its own (all kinds, any importance, all time).
  const filters: NearbyFilters = {
    ...(kinds.length < KINDS.length && kinds.length > 0 && { kinds }),
    ...(requestedMinImportance > 0 && { minImportance: requestedMinImportance }),
    ...(requestedRange !== null && { from: requestedRange.from, to: requestedRange.to }),
  };
  const events = useEvents(bbox === null || noKinds ? null : { bbox, ...filters });
  const timeline = useTimeline(bbox);
  const shown = noKinds ? [] : events.data?.events ?? [];
  const selectedId = panel?.kind === "details" ? panel.id : null;
  const place = panel === null ? null : panel.kind === "nearby" ? panel.place : panel.fromPlace;
  // The same query the details panel runs (TanStack shares it), for the selection ring.
  const selectedEvent = useEvent(selectedId).data?.event ?? null;
  const close = () => setPanel(null);
  const back = panel?.kind === "details" && panel.fromPlace !== null
    ? { place: panel.fromPlace, focusId: panel.id }
    : null;

  return (
    <div className="app">
      <MapView
        ref={mapRef}
        events={shown}
        onViewportChange={setBbox}
        selectedId={selectedId}
        selectedEvent={selectedEvent}
        onSelect={(id) => setPanel({ kind: "details", id, fromPlace: null })}
        onPlaceClick={(clicked) => setPanel({ kind: "nearby", place: clicked })}
        place={place}
      />
      <MapNotices
        noKinds={noKinds}
        truncated={!noKinds && events.data?.truncated === true}
        error={noKinds ? null : events.error?.message ?? null}
      />
      <SidePanel
        kinds={kinds}
        onKindsChange={setKinds}
        minImportance={minImportance}
        onMinImportanceChange={setMinImportance}
      />
      {panel?.kind === "details" && (
        <DetailsPanel
          id={panel.id}
          onClose={close}
          returnFocusTo={mapRef}
          {...(back !== null && { onBack: () => setPanel({ kind: "nearby", ...back }) })}
        />
      )}
      {panel?.kind === "nearby" && (
        <NearbyPanel
          place={panel.place}
          filters={noKinds ? null : filters}
          {...(panel.focusId !== undefined && { focusId: panel.focusId })}
          onSelect={(id) => setPanel({ kind: "details", id, fromPlace: panel.place })}
          onClose={close}
          returnFocusTo={mapRef}
        />
      )}
      <TimeBar decades={timeline.data?.decades ?? []} range={range} onRangeChange={setRange} />
    </div>
  );
}
