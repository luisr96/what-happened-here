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

const EARTH_RADIUS_M = 6371008.8;
const toRadians = (degrees: number) => (degrees * Math.PI) / 180;

type LatLon = { readonly lat: number; readonly lon: number };

/** Great-circle distance in metres (haversine), the short way round across the antimeridian. */
export function distanceMetres(a: LatLon, b: LatLon): number {
  const dLat = toRadians(b.lat - a.lat);
  const dLon = toRadians(b.lon - a.lon);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Where the resize handle sits: due east of the centre, `radius` away along its parallel. */
export function edgePoint(place: Place): LatLon {
  const lon = place.lon + (place.radius / (EARTH_RADIUS_M * Math.cos(toRadians(place.lat)))) * (180 / Math.PI);
  return { lat: place.lat, lon: ((((lon + 180) % 360) + 360) % 360) - 180 };
}

/** The smallest area "what happened here" searches, for a click and the handle and slider alike. */
export const MIN_SEARCH_RADIUS_M = 100;

/** A radius the API accepts: whole metres within 1..500000. */
export function clampRadius(metres: number): number {
  return Math.min(MAX_RADIUS_M, Math.max(1, Math.round(metres)));
}

export type DistanceUnits = "km" | "mi";

const METRES_PER_MILE = 1609.344;
const METRES_PER_FOOT = 0.3048;

/** "800 m", "2.3 km", "12 km"; in miles "520 ft" (below 0.1 mi), "2.3 mi", "12 mi". */
export function formatDistance(metres: number, units: DistanceUnits = "km"): string {
  if (units === "mi") {
    const miles = metres / METRES_PER_MILE;
    if (miles < 0.1) return `${Math.round(metres / METRES_PER_FOOT / 10) * 10} ft`;
    return miles >= 10 ? `${Math.round(miles)} mi` : `${miles.toFixed(1)} mi`;
  }
  if (metres < 1000) return `${Math.round(metres)} m`;
  const km = metres / 1000;
  return km >= 10 ? `${Math.round(km)} km` : `${km.toFixed(1)} km`;
}
