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
import { styleUrl, useSettings } from "./settings/settings";
import { TimeBar } from "./timebar/TimeBar";
import type { YearRange } from "./timebar/timeScale";
import { useThrottledValue } from "./util/useThrottledValue";

// The time range is throttled: the map keeps following while the range is dragged, at most one
// request per 250 ms. Importance is a three-way choice, so it applies at once.
const RANGE_THROTTLE_MS = 250;

/**
 * The one panel on the right, if any. Details opened from the nearby list remember its place, so
 * "Back to list" can return to it and the searched area stays drawn meanwhile.
 */
type RightPanel =
  | { readonly kind: "details"; readonly id: string; readonly fromPlace: Place | null; readonly searchId?: number }
  | { readonly kind: "nearby"; readonly place: Place; readonly searchId: number; readonly focusId?: string }
  | null;

export function App() {
  const [bbox, setBbox] = useState<Bbox | null>(null);
  const [kinds, setKinds] = useState<readonly Kind[]>(KINDS);
  const [minImportance, setMinImportance] = useState(0);
  const [range, setRange] = useState<YearRange | null>(null);
  const [panel, setPanel] = useState<RightPanel>(null);
  // One number per map click: the nearby panel is keyed by it, so each click gets a fresh panel
  // (searched at once) while moves and resizes update the same one (throttled).
  const searchCount = useRef(0);
  const [settings, setSettings] = useSettings();
  const mapRef = useRef<HTMLElement>(null);

  const requestedRange = useThrottledValue(range, RANGE_THROTTLE_MS);

  const noKinds = kinds.length === 0;
  // Shared by the map and "what happened here", so the list matches the map. Defaults are left
  // out of the request, so the API applies its own (all kinds, any importance, all time).
  const filters: NearbyFilters = {
    ...(kinds.length < KINDS.length && kinds.length > 0 && { kinds }),
    ...(minImportance > 0 && { minImportance }),
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
  // Resizing keeps the centre; only the open "what happened here" list can be resized.
  const resize = (radius: number) =>
    setPanel((current) => (current?.kind === "nearby" ? { ...current, place: { ...current.place, radius } } : current));
  const back = panel?.kind === "details" && panel.fromPlace !== null
    ? { place: panel.fromPlace, focusId: panel.id, searchId: panel.searchId ?? 0 }
    : null;
  // Moving keeps the radius; like resizing, only the open "what happened here" list can be moved.
  const move = (centre: { lat: number; lon: number }) =>
    setPanel((current) => (current?.kind === "nearby" ? { ...current, place: { ...current.place, ...centre } } : current));

  return (
    <div className="app">
      <MapView
        ref={mapRef}
        events={shown}
        onViewportChange={setBbox}
        selectedId={selectedId}
        selectedEvent={selectedEvent}
        onSelect={(id) => setPanel({ kind: "details", id, fromPlace: null })}
        onPlaceClick={(clicked) => setPanel({ kind: "nearby", place: clicked, searchId: ++searchCount.current })}
        place={place}
        {...(panel?.kind === "nearby" && { onPlaceRadiusChange: resize, onPlaceMove: move })}
        mapStyleUrl={styleUrl(settings.mapStyle)}
      />
      <MapNotices
        noKinds={noKinds}
        error={noKinds ? null : events.error?.message ?? null}
      />
      <SidePanel
        kinds={kinds}
        onKindsChange={setKinds}
        minImportance={minImportance}
        onMinImportanceChange={setMinImportance}
        settings={settings}
        onSettingsChange={setSettings}
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
          key={panel.searchId}
          place={panel.place}
          filters={noKinds ? null : filters}
          {...(panel.focusId !== undefined && { focusId: panel.focusId })}
          onSelect={(id) => setPanel({ kind: "details", id, fromPlace: panel.place, searchId: panel.searchId })}
          onClose={close}
          returnFocusTo={mapRef}
          onRadiusChange={resize}
          units={settings.units}
        />
      )}
      <TimeBar decades={timeline.data?.decades ?? []} range={range} onRangeChange={setRange} />
    </div>
  );
}
