import { useRef, useState } from "react";
import type { Bbox, Kind } from "./api/client";
import { useEvent, useEvents, useTimeline } from "./api/hooks";
import { DetailsPanel } from "./details/DetailsPanel";
import { MapNotices } from "./map/MapNotices";
import { MapView } from "./map/MapView";
import { KINDS } from "./map/kinds";
import { SidePanel } from "./panel/SidePanel";
import { TimeBar } from "./timebar/TimeBar";
import type { YearRange } from "./timebar/timeScale";
import { useDebouncedValue } from "./util/useDebouncedValue";
import { useThrottledValue } from "./util/useThrottledValue";

// The importance slider only matters where it stops, so it's debounced. The time range is
// throttled instead: the map keeps following while the range is dragged.
const SLIDER_SETTLE_MS = 250;
const RANGE_THROTTLE_MS = 250;

export function App() {
  const [bbox, setBbox] = useState<Bbox | null>(null);
  const [kinds, setKinds] = useState<readonly Kind[]>(KINDS);
  const [minImportance, setMinImportance] = useState(0);
  const [range, setRange] = useState<YearRange | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const mapRef = useRef<HTMLElement>(null);

  const requestedMinImportance = useDebouncedValue(minImportance, SLIDER_SETTLE_MS);
  const requestedRange = useThrottledValue(range, RANGE_THROTTLE_MS);

  const noKinds = kinds.length === 0;
  // Defaults are left out of the request, so the API applies its own (all kinds, any importance).
  const events = useEvents(bbox === null || noKinds ? null : {
    bbox,
    ...(kinds.length < KINDS.length && { kinds }),
    ...(requestedMinImportance > 0 && { minImportance: requestedMinImportance }),
    ...(requestedRange !== null && { from: requestedRange.from, to: requestedRange.to }),
  });
  const timeline = useTimeline(bbox);
  const shown = noKinds ? [] : events.data?.events ?? [];
  // The same query the details panel runs (TanStack shares it), for the selection ring.
  const selectedEvent = useEvent(selectedId).data?.event ?? null;

  return (
    <div className="app">
      <MapView
        ref={mapRef}
        events={shown}
        onViewportChange={setBbox}
        selectedId={selectedId}
        selectedEvent={selectedEvent}
        onSelect={setSelectedId}
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
      {selectedId !== null && (
        <DetailsPanel id={selectedId} onClose={() => setSelectedId(null)} returnFocusTo={mapRef} />
      )}
      <TimeBar decades={timeline.data?.decades ?? []} range={range} onRangeChange={setRange} />
    </div>
  );
}
