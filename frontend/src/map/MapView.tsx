import type { PickingInfo } from "@deck.gl/core";
import { MapboxOverlay, type MapboxOverlayProps } from "@deck.gl/mapbox";
import { useMemo, useState, type Ref } from "react";
import type { LngLatBounds } from "maplibre-gl";
import { Map, Marker, useControl } from "react-map-gl/maplibre";
import type { Bbox } from "../api/client";
import type { EventView } from "../api/hooks";
import { viewportToBbox } from "./bbox";
import { placeLayer, pointLayers, selectionLayer } from "./eventLayers";
import {
  clampRadius, clickRadiusMetres, distanceMetres, edgePoint, MIN_SEARCH_RADIUS_M, type Place,
} from "./nearby";
import { tooltipText } from "./tooltip";

/**
 * deck.gl on its own canvas above the map (overlaid), so points cover map labels. Interleaved
 * mode, which would draw inside MapLibre's canvas under the labels, crashes with deck.gl 9.4
 * and MapLibre 6 as soon as a layer exists ("reading '_nearZ'"); revisit on a newer deck.gl.
 */
function DeckOverlay(props: MapboxOverlayProps) {
  const overlay = useControl(() => new MapboxOverlay({ ...props, interleaved: false }));
  overlay.setProps(props);
  return null;
}

export type MapViewProps = {
  readonly events: readonly EventView[];
  readonly onViewportChange: (bbox: Bbox) => void;
  readonly selectedId: string | null;
  /**
   * The selected event as loaded for the details panel, so its ring stays drawn after a pan or
   * filter drops it from `events`; until it loads, the ring uses the event from `events`.
   */
  readonly selectedEvent?: EventView | null;
  readonly onSelect: (id: string) => void;
  /** A click where no event is: "what happened here" around that spot. */
  readonly onPlaceClick: (place: Place) => void;
  /** The area being listed, drawn as a faint disc; null when none. */
  readonly place: Place | null;
  /** When given, the area gets a handle on its east edge; dragging it reports a new radius. */
  readonly onPlaceRadiusChange?: (radius: number) => void;
  /** The basemap style (an OpenFreeMap style URL). */
  readonly mapStyleUrl: string;
  /** The map region, focusable from script (e.g. when the details panel closes). */
  readonly ref?: Ref<HTMLElement>;
};

// deck.gl styles its tooltip inline; these override its defaults to match the side panel.
const MAX_HANDLE_LAT = 85;

const TOOLTIP_STYLE = {
  whiteSpace: "pre-line",
  padding: "6px 8px",
  background: "rgb(22 24 29 / 92%)",
  border: "1px solid #2a2d35",
  borderRadius: "4px",
  color: "#e6e7ea",
  font: "inherit",
};

function tooltip({ object }: PickingInfo<EventView>) {
  if (object === undefined) return null;
  try {
    return { text: tooltipText(object), style: TOOLTIP_STYLE };
  } catch (error) {
    // An exception thrown here stops deck.gl's hovering and redrawing for good, so one event's
    // bad data (or a bug in formatting it) must not escape: log it and show the name alone.
    console.error("Could not build the tooltip for", object.id, error);
    return { text: object.label ?? object.itemId, style: TOOLTIP_STYLE };
  }
}

export function MapView({
  events, onViewportChange, selectedId, selectedEvent, onSelect, onPlaceClick, place, onPlaceRadiusChange, mapStyleUrl, ref,
}: MapViewProps) {
  // Busy until MapLibre's load event, which fires once the style has loaded.
  const [loaded, setLoaded] = useState(false);
  // Memoized so deck.gl sees the same layers between renders and doesn't re-diff its data.
  // The ring is memoized apart, so selecting an event leaves the point layers untouched.
  const points = useMemo(() => pointLayers(events), [events]);
  const selected = selectedEvent ?? (selectedId === null ? null : events.find((e) => e.id === selectedId) ?? null);
  const ring = useMemo(() => selectionLayer(selected), [selected]);
  const area = useMemo(() => placeLayer(place), [place]);
  // No handle near the poles, where a point due east along the parallel stops making sense;
  // the panel's slider still resizes there.
  const edge = place === null || Math.abs(place.lat) > MAX_HANDLE_LAT ? null : edgePoint(place);
  // The searched area lies under the points so they stay hoverable and clickable inside it.
  const layers = useMemo(() => [area, ...points, ring], [area, points, ring]);

  // Reported once loaded and then after each pan or zoom ends, not on every animation frame.
  const report = (map: { getBounds(): LngLatBounds }) => {
    const bounds = map.getBounds();
    onViewportChange(viewportToBbox({
      west: bounds.getWest(),
      south: bounds.getSouth(),
      east: bounds.getEast(),
      north: bounds.getNorth(),
    }));
  };

  return (
    <section className="map" aria-label="Map" aria-busy={!loaded} ref={ref} tabIndex={-1}>
      <Map
        onLoad={(e) => {
          setLoaded(true);
          report(e.target);
        }}
        onMoveEnd={(e) => report(e.target)}
        initialViewState={{ longitude: 10, latitude: 30, zoom: 2 }}
        mapStyle={mapStyleUrl}
        // OpenFreeMap and OpenStreetMap are credited by the style's source (its TileJSON).
        attributionControl={{ compact: false }}
        // MapLibre labels its canvas "Map" too; a distinct name keeps the landmark unique.
        locale={{ "Map.Title": "Pan and zoom area" }}
      >
        {place !== null && edge !== null && onPlaceRadiusChange !== undefined && (
          // An HTML marker rather than a deck.gl layer: it handles its own drag, so the map
          // doesn't pan while the handle moves. The radius is the true distance to the pointer.
          <Marker
            longitude={edge.lon}
            latitude={edge.lat}
            draggable
            onDrag={(e) => {
              const metres = distanceMetres(place, { lat: e.lngLat.lat, lon: e.lngLat.lng });
              onPlaceRadiusChange(clampRadius(Math.max(MIN_SEARCH_RADIUS_M, metres)));
            }}
          >
            {/* Pointer only; keyboard and screen-reader users resize with the panel's slider. */}
            <div className="area-handle" title="Drag to resize the search area" aria-hidden="true" />
          </Marker>
        )}
        <DeckOverlay
          layers={layers}
          getTooltip={tooltip}
          onClick={({ object, coordinate, viewport }: PickingInfo<EventView>) => {
            if (object !== undefined) {
              onSelect(object.id);
            } else if (coordinate !== undefined && viewport !== undefined) {
              const [lon, lat] = coordinate;
              if (lon === undefined || lat === undefined) return;
              // Past the antimeridian the map reports longitudes beyond ±180; the API wants -180..180.
              const wrapped = ((((lon + 180) % 360) + 360) % 360) - 180;
              const radius = Math.max(MIN_SEARCH_RADIUS_M, clickRadiusMetres(lat, viewport.zoom));
              onPlaceClick({ lat, lon: wrapped, radius });
            }
          }}
        />
      </Map>
    </section>
  );
}
