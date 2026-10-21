import type { components } from "./schema";

/** Viewport in degrees; `minLon > maxLon` means it crosses the antimeridian (see /events `bbox`). */
export type Bbox = {
  readonly minLon: number;
  readonly minLat: number;
  readonly maxLon: number;
  readonly maxLat: number;
};

export type Kind = components["schemas"]["EventView"]["kind"];

export type Params = Readonly<Record<string, string | number | readonly string[] | undefined>>;

/** A failed API call; `message` is fit to show the user. `status` is 0 when the server wasn't reached. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export function bboxParam(bbox: Bbox): string {
  return `${bbox.minLon},${bbox.minLat},${bbox.maxLon},${bbox.maxLat}`;
}

/**
 * Where API paths are appended. By default `/api` on the page's own origin (the Vite dev proxy
 * strips it). A build for a static host on another origin sets `VITE_API_BASE_URL` to the API's
 * absolute URL (e.g. GitHub Pages calling the API on its own server). A malformed value is a build mistake, so it
 * throws rather than sending requests somewhere unexpected.
 */
export function apiBaseUrl(env: { VITE_API_BASE_URL?: string | undefined }): string {
  const configured = env.VITE_API_BASE_URL?.trim() ?? "";
  if (configured === "") return `${window.location.origin}/api`;
  if (!/^https?:\/\/[^/]/.test(configured)) {
    throw new Error(`VITE_API_BASE_URL must be an absolute http(s) URL, got "${configured}"`);
  }
  return configured.replace(/\/+$/, "");
}

/** `base` + `path` with the query: undefined params left out, arrays sent comma-separated. */
export function apiUrl(base: string, path: string, params: Params): URL {
  const url = new URL(`${base}${path}`);
  for (const [name, value] of Object.entries(params)) {
    if (value === undefined) continue;
    url.searchParams.set(name, typeof value === "object" ? value.join(",") : String(value));
  }
  return url;
}

/**
 * GET `path` from the API (see {@link apiBaseUrl}). An abort rejects with the `AbortError`
 * itself, not an `ApiError`, so callers can tell a cancelled request from a failed one.
 */
export async function apiGet<T>(path: string, params: Params, signal?: AbortSignal): Promise<T> {
  const url = apiUrl(apiBaseUrl(import.meta.env), path, params);

  let response: Response;
  try {
    response = await fetch(url, signal === undefined ? {} : { signal });
  } catch (error) {
    // Ask the signal rather than the error's class: DOMException differs between realms (jsdom).
    if (signal?.aborted === true) throw error;
    throw new ApiError(0, "Could not reach the server");
  }
  if (!response.ok) throw new ApiError(response.status, await problemMessage(response));
  return (await response.json()) as T;
}

/** The RFC 9457 problem's `detail`, else its `title`, else a generic text (the body may not be JSON). */
async function problemMessage(response: Response): Promise<string> {
  try {
    const problem: unknown = await response.json();
    if (typeof problem === "object" && problem !== null) {
      const { detail, title } = problem as { detail?: unknown; title?: unknown };
      if (typeof detail === "string" && detail !== "") return detail;
      if (typeof title === "string" && title !== "") return title;
    }
  } catch {
    // Not JSON (e.g. a proxy's HTML error page): fall through to the generic message.
  }
  return `The server returned an error (${response.status})`;
}
