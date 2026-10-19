/** Viewport in degrees; `minLon > maxLon` means it crosses the antimeridian (see /events `bbox`). */
export type Bbox = {
  readonly minLon: number;
  readonly minLat: number;
  readonly maxLon: number;
  readonly maxLat: number;
};

export type Kind = "HAPPENED" | "BORN" | "DIED" | "FOUNDED" | "ENDED" | "OPENED";

type Params = Readonly<Record<string, string | number | readonly string[] | undefined>>;

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
 * GET `/api` + `path` (the dev proxy and hosting strip `/api`). Undefined params are left out,
 * arrays are sent comma-separated. An abort rejects with the `AbortError` itself, not an
 * `ApiError`, so callers can tell a cancelled request from a failed one.
 */
export async function apiGet<T>(path: string, params: Params, signal?: AbortSignal): Promise<T> {
  const url = new URL(`/api${path}`, window.location.origin);
  for (const [name, value] of Object.entries(params)) {
    if (value === undefined) continue;
    url.searchParams.set(name, typeof value === "object" ? value.join(",") : String(value));
  }

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
