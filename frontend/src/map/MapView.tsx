import type { PickingInfo } from "@deck.gl/core";
import { MapboxOverlay, type MapboxOverlayProps } from "@deck.gl/mapbox";
import { useMemo, useState } from "react";
import type { LngLatBounds } from "maplibre-gl";
import { Map, useControl } from "react-map-gl/maplibre";
import type { Bbox } from "../api/client";
import type { EventView } from "../api/hooks";
import { viewportToBbox } from "./bbox";
import { eventLayers } from "./eventLayers";
import { tooltipText } from "./tooltip";

const STYLE_URL = "https://tiles.openfreemap.org/styles/dark";

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
};

// deck.gl styles its tooltip inline; these override its defaults to match the side panel.
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
  return object === undefined ? null : { text: tooltipText(object), style: TOOLTIP_STYLE };
}

export function MapView({ events, onViewportChange }: MapViewProps) {
  // Busy until MapLibre's load event, which fires once the style has loaded.
  const [loaded, setLoaded] = useState(false);
  // Memoized so deck.gl sees the same layers between renders and doesn't re-diff its data.
  const layers = useMemo(() => eventLayers(events), [events]);

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
    <section className="map" aria-label="Map" aria-busy={!loaded}>
      <Map
        onLoad={(e) => {
          setLoaded(true);
          report(e.target);
        }}
        onMoveEnd={(e) => report(e.target)}
        initialViewState={{ longitude: 10, latitude: 30, zoom: 2 }}
        mapStyle={STYLE_URL}
        // OpenFreeMap and OpenStreetMap are credited by the style's source (its TileJSON).
        attributionControl={{ compact: false }}
        // MapLibre labels its canvas "Map" too; a distinct name keeps the landmark unique.
        locale={{ "Map.Title": "Pan and zoom area" }}
      >
        <DeckOverlay layers={layers} getTooltip={tooltip} />
      </Map>
    </section>
  );
}
