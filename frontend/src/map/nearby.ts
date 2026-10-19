/** A clicked spot and how far around it "what happened here" looks. */
export type Place = { readonly lat: number; readonly lon: number; readonly radius: number };

const EARTH_CIRCUMFERENCE_M = 40075016.686;
const TILE_SIZE_PX = 512;
/** The API's limit for /places/nearby `radius`. */
const MAX_RADIUS_M = 500000;

/**
 * The ground distance `pixels` screen pixels cover at `lat` and `zoom` (Web Mercator, MapLibre's
 * 512 px tiles): a click searches the same patch of screen whatever the zoom. Whole metres,
 * within the API's 1..500000.
 */
export function clickRadiusMetres(lat: number, zoom: number, pixels = 30): number {
  const metresPerPixel = (EARTH_CIRCUMFERENCE_M * Math.cos((lat * Math.PI) / 180)) / (TILE_SIZE_PX * 2 ** zoom);
  return Math.min(MAX_RADIUS_M, Math.max(1, Math.round(pixels * metresPerPixel)));
}

/** "800 m", "2.3 km", "12 km". */
export function formatDistance(metres: number): string {
  if (metres < 1000) return `${Math.round(metres)} m`;
  const km = metres / 1000;
  return km >= 10 ? `${Math.round(km)} km` : `${km.toFixed(1)} km`;
}
