import { useState } from "react";
import type { Bbox, Kind } from "./api/client";
import { useEvents } from "./api/hooks";
import { MapNotices } from "./map/MapNotices";
import { MapView } from "./map/MapView";
import { KINDS } from "./map/kinds";
import { SidePanel } from "./panel/SidePanel";
import { TimeBar } from "./timebar/TimeBar";
import { useDebouncedValue } from "./util/useDebouncedValue";

// Dragging the slider passes through many values; only the one it stops at is requested.
const SLIDER_SETTLE_MS = 250;

export function App() {
  const [bbox, setBbox] = useState<Bbox | null>(null);
  const [kinds, setKinds] = useState<readonly Kind[]>(KINDS);
  const [minImportance, setMinImportance] = useState(0);

  const requestedMinImportance = useDebouncedValue(minImportance, SLIDER_SETTLE_MS);

  const noKinds = kinds.length === 0;
  // Defaults are left out of the request, so the API applies its own (all kinds, any importance).
  const events = useEvents(bbox === null || noKinds ? null : {
    bbox,
    ...(kinds.length < KINDS.length && { kinds }),
    ...(requestedMinImportance > 0 && { minImportance: requestedMinImportance }),
  });
  const shown = noKinds ? [] : events.data?.events ?? [];

  return (
    <div className="app">
      <MapView events={shown} onViewportChange={setBbox} />
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
      <TimeBar />
    </div>
  );
}
