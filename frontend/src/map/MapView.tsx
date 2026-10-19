import { MapboxOverlay, type MapboxOverlayProps } from "@deck.gl/mapbox";
import { useState } from "react";
import { Map, useControl } from "react-map-gl/maplibre";

const STYLE_URL = "https://tiles.openfreemap.org/styles/dark";

/** deck.gl drawn inside MapLibre's own canvas (interleaved), so map labels can sit on top. */
function DeckOverlay(props: MapboxOverlayProps) {
  const overlay = useControl(() => new MapboxOverlay({ ...props, interleaved: true }));
  overlay.setProps(props);
  return null;
}

export function MapView() {
  // Busy until MapLibre's load event, which fires once the style has loaded.
  const [loaded, setLoaded] = useState(false);

  return (
    <section className="map" aria-label="Map" aria-busy={!loaded}>
      <Map
        onLoad={() => setLoaded(true)}
        initialViewState={{ longitude: 10, latitude: 30, zoom: 2 }}
        mapStyle={STYLE_URL}
        // OpenFreeMap and OpenStreetMap are credited by the style's source (its TileJSON).
        attributionControl={{ compact: false }}
        // MapLibre labels its canvas "Map" too; a distinct name keeps the landmark unique.
        locale={{ "Map.Title": "Pan and zoom area" }}
      >
        <DeckOverlay layers={[]} />
      </Map>
    </section>
  );
}
