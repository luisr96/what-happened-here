import type { Bbox } from "../api/client";

export type Bounds = {
  readonly west: number;
  readonly south: number;
  readonly east: number;
  readonly north: number;
};

/**
 * The API's bbox for a MapLibre view. After panning across the antimeridian MapLibre reports
 * longitudes beyond ±180 (e.g. 170..190); they're folded back into range, which makes a view
 * across the antimeridian come out as minLon > maxLon, the API's crossing box. A view 360
 * degrees wide or more is the whole world.
 */
export function viewportToBbox(bounds: Bounds): Bbox {
  const minLat = clampLat(bounds.south);
  const maxLat = clampLat(bounds.north);
  if (bounds.east - bounds.west >= 360) {
    return { minLon: -180, minLat, maxLon: 180, maxLat };
  }
  const east = wrapLon(bounds.east);
  // Folding maps 180 to -180; as an east edge it must stay 180 or the box would cross.
  return { minLon: wrapLon(bounds.west), minLat, maxLon: east === -180 ? 180 : east, maxLat };
}

/** Into [-180, 180). */
function wrapLon(lon: number): number {
  return ((((lon + 180) % 360) + 360) % 360) - 180;
}

function clampLat(lat: number): number {
  return Math.min(90, Math.max(-90, lat));
}
