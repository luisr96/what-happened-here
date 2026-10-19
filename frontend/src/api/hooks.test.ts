import { renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { server } from "../test/server";
import { createQueryWrapper } from "../test/queryWrapper";
import {
  badBboxProblem,
  caesarDetails,
  caesarDied,
  emptyEvents,
  eventsPage,
  mozartDetailsNoWikipedia,
  otherEventsPage,
  otherTimeline,
  problemJson,
  timeline,
} from "../test/apiFixtures";
import { ApiError } from "./client";
import type { Bbox, Kind } from "./client";
import { useEvent, useEvents, useNearby, useTimeline } from "./hooks";

const europe: Bbox = { minLon: -10, minLat: 35, maxLon: 30, maxLat: 60 };
const pacific: Bbox = { minLon: 170, minLat: -10, maxLon: -170, maxLat: 10 };
const EUROPE = "-10,35,30,60";
const PACIFIC = "170,-10,-170,10";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

type Recorded = { url: URL; signal: AbortSignal };
type NearbyQuery = { lat: number; lon: number; radius: number };

/** Records every request URL and its signal, so tests can check later whether it was aborted. */
function recorder() {
  const requests: Recorded[] = [];
  return {
    requests,
    record(request: Request) {
      requests.push({ url: new URL(request.url), signal: request.signal });
    },
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useEvents", () => {
  it("is pending with no data at first, then has the response as data", async () => {
    server.use(http.get("*/api/events", () => HttpResponse.json(eventsPage)));
    const { result } = renderHook(() => useEvents({ bbox: europe }), { wrapper: createQueryWrapper() });
    expect(result.current.isPending).toBe(true);
    expect(result.current.data).toBeUndefined();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(eventsPage);
  });

  it("keeps BC years, date precision and approximate coordinates from the response", async () => {
    server.use(http.get("*/api/events", () => HttpResponse.json(eventsPage)));
    const { result } = renderHook(() => useEvents({ bbox: europe }), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.events?.[0]?.when).toEqual(caesarDied.when);
    expect(result.current.data?.events?.[1]).toMatchObject({ approximate: true, coordPrecisionDeg: 1 });
  });

  it("succeeds with an empty list when nothing matched", async () => {
    server.use(http.get("*/api/events", () => HttpResponse.json(emptyEvents)));
    const { result } = renderHook(() => useEvents({ bbox: europe }), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ events: [], truncated: false });
  });

  it("passes the truncated flag through", async () => {
    server.use(http.get("*/api/events", () => HttpResponse.json(otherEventsPage)));
    const { result } = renderHook(() => useEvents({ bbox: europe }), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.truncated).toBe(true);
  });

  it("sends bbox as minLon,minLat,maxLon,maxLat and every given filter", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(eventsPage);
      }),
    );
    const { result } = renderHook(
      () => useEvents({ bbox: europe, from: -500, to: 1200, kinds: ["BORN", "DIED"], minImportance: 5, limit: 100 }),
      { wrapper: createQueryWrapper() },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const q = rec.requests[0]?.url.searchParams;
    expect(q?.get("bbox")).toBe(EUROPE);
    expect(q?.get("from")).toBe("-500");
    expect(q?.get("to")).toBe("1200");
    expect(q?.get("kinds")).toBe("BORN,DIED");
    expect(q?.get("minImportance")).toBe("5");
    expect(q?.get("limit")).toBe("100");
  });

  it("sends an antimeridian-crossing bbox unchanged, with minLon greater than maxLon", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(otherEventsPage);
      }),
    );
    const { result } = renderHook(() => useEvents({ bbox: pacific }), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(rec.requests).toHaveLength(1);
    expect(rec.requests[0]?.url.searchParams.get("bbox")).toBe(PACIFIC);
  });

  it("does not send optional parameters that are absent", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(eventsPage);
      }),
    );
    const { result } = renderHook(() => useEvents({ bbox: europe }), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect([...(rec.requests[0]?.url.searchParams.keys() ?? [])]).toEqual(["bbox"]);
  });

  it("sends BC year bounds as negative numbers", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(eventsPage);
      }),
    );
    const { result } = renderHook(() => useEvents({ bbox: europe, from: -3000, to: -1 }), {
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(rec.requests[0]?.url.searchParams.get("from")).toBe("-3000");
    expect(rec.requests[0]?.url.searchParams.get("to")).toBe("-1");
  });

  it("exposes a 400 problem as an ApiError whose message is the detail", async () => {
    server.use(
      http.get("*/api/events", () => HttpResponse.json(badBboxProblem, { status: 400, headers: problemJson })),
    );
    const { result } = renderHook(() => useEvents({ bbox: europe }), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(ApiError);
    expect(result.current.error).toMatchObject({ status: 400, message: "bbox must be minLon,minLat,maxLon,maxLat" });
  });

  it("exposes an unreachable server as an ApiError with status 0", async () => {
    server.use(http.get("*/api/events", () => HttpResponse.error()));
    const { result } = renderHook(() => useEvents({ bbox: europe }), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toMatchObject({ status: 0, message: "Could not reach the server" });
  });

  it("aborts the stale request and never shows its late response as data", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events", async ({ request }) => {
        rec.record(request);
        if (new URL(request.url).searchParams.get("bbox") === EUROPE) {
          await sleep(150);
          return HttpResponse.json(eventsPage);
        }
        return HttpResponse.json(otherEventsPage);
      }),
    );
    const seen: unknown[] = [];
    const { result, rerender } = renderHook(
      ({ bbox }: { bbox: Bbox }) => {
        const query = useEvents({ bbox });
        seen.push(query.data);
        return query;
      },
      { initialProps: { bbox: europe }, wrapper: createQueryWrapper() },
    );
    await waitFor(() => expect(rec.requests).toHaveLength(1));
    rerender({ bbox: pacific });
    await waitFor(() => expect(result.current.data).toEqual(otherEventsPage));
    await sleep(250);
    expect(result.current.data).toEqual(otherEventsPage);
    expect(seen).not.toContainEqual(eventsPage);
    expect(rec.requests[0]?.signal.aborted).toBe(true);
  });

  it("keeps the previous data as placeholder while a new query loads", async () => {
    server.use(
      http.get("*/api/events", async ({ request }) => {
        if (new URL(request.url).searchParams.get("bbox") === PACIFIC) {
          await sleep(100);
          return HttpResponse.json(otherEventsPage);
        }
        return HttpResponse.json(eventsPage);
      }),
    );
    const { result, rerender } = renderHook(({ bbox }: { bbox: Bbox }) => useEvents({ bbox }), {
      initialProps: { bbox: europe },
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.data).toEqual(eventsPage));
    expect(result.current.isPlaceholderData).toBe(false);
    rerender({ bbox: pacific });
    await waitFor(() => expect(result.current.isFetching).toBe(true));
    expect(result.current.data).toEqual(eventsPage);
    expect(result.current.isPlaceholderData).toBe(true);
    await waitFor(() => expect(result.current.data).toEqual(otherEventsPage));
    expect(result.current.isPlaceholderData).toBe(false);
  });

  it("does not refetch for a new query object with equal values", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(eventsPage);
      }),
    );
    const { result, rerender } = renderHook(
      ({ q }: { q: { bbox: Bbox; kinds: readonly Kind[] } }) => useEvents(q),
      { initialProps: { q: { bbox: { ...europe }, kinds: ["BORN", "DIED"] } }, wrapper: createQueryWrapper() },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    rerender({ q: { bbox: { ...europe }, kinds: ["BORN", "DIED"] } });
    rerender({ q: { bbox: { ...europe }, kinds: ["BORN", "DIED"] } });
    await sleep(50);
    expect(rec.requests).toHaveLength(1);
    expect(result.current.isFetching).toBe(false);
  });

  it("refetches when only a filter value changes", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(eventsPage);
      }),
    );
    const { result, rerender } = renderHook(
      ({ min }: { min: number }) => useEvents({ bbox: europe, minImportance: min }),
      { initialProps: { min: 1 }, wrapper: createQueryWrapper() },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    rerender({ min: 10 });
    await waitFor(() => expect(rec.requests).toHaveLength(2));
    expect(rec.requests[1]?.url.searchParams.get("minImportance")).toBe("10");
  });

  it("aborts the in-flight request on unmount without console errors", async () => {
    const consoleError = vi.spyOn(console, "error");
    const rec = recorder();
    server.use(
      http.get("*/api/events", async ({ request }) => {
        rec.record(request);
        await sleep(100);
        return HttpResponse.json(eventsPage);
      }),
    );
    const { unmount } = renderHook(() => useEvents({ bbox: europe }), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(rec.requests).toHaveLength(1));
    unmount();
    await waitFor(() => expect(rec.requests[0]?.signal.aborted).toBe(true));
    await sleep(150);
    expect(consoleError).not.toHaveBeenCalled();
  });

  it("never reports an aborted request as an error", async () => {
    const seen: boolean[] = [];
    server.use(
      http.get("*/api/events", async ({ request }) => {
        if (new URL(request.url).searchParams.get("bbox") === EUROPE) await sleep(100);
        return HttpResponse.json(eventsPage);
      }),
    );
    const { result, rerender } = renderHook(
      ({ bbox }: { bbox: Bbox }) => {
        const query = useEvents({ bbox });
        seen.push(query.isError);
        return query;
      },
      { initialProps: { bbox: europe }, wrapper: createQueryWrapper() },
    );
    rerender({ bbox: pacific });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await sleep(150);
    expect(seen).not.toContain(true);
  });
});

describe("useTimeline", () => {
  it("stays idle and sends no request while the bbox is null", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/stats/timeline", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(timeline);
      }),
    );
    const { result } = renderHook(() => useTimeline(null), { wrapper: createQueryWrapper() });
    await sleep(50);
    expect(rec.requests).toHaveLength(0);
    expect(result.current.fetchStatus).toBe("idle");
    expect(result.current.data).toBeUndefined();
  });

  it("loads decade counts for the bbox", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/stats/timeline", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(timeline);
      }),
    );
    const { result } = renderHook(() => useTimeline(pacific), { wrapper: createQueryWrapper() });
    expect(result.current.isPending).toBe(true);
    await waitFor(() => expect(result.current.data).toEqual(timeline));
    expect(rec.requests[0]?.url.searchParams.get("bbox")).toBe(PACIFIC);
  });

  it("exposes a 400 problem as an ApiError whose message is the detail", async () => {
    server.use(
      http.get("*/api/stats/timeline", () =>
        HttpResponse.json({ ...badBboxProblem, instance: "/stats/timeline" }, { status: 400, headers: problemJson }),
      ),
    );
    const { result } = renderHook(() => useTimeline(europe), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(ApiError);
    expect(result.current.error?.message).toBe("bbox must be minLon,minLat,maxLon,maxLat");
  });

  it("keeps previous counts as placeholder and ignores a late response for an old bbox", async () => {
    server.use(
      http.get("*/api/stats/timeline", async ({ request }) => {
        if (new URL(request.url).searchParams.get("bbox") === PACIFIC) {
          await sleep(150);
          return HttpResponse.json(timeline);
        }
        return HttpResponse.json(otherTimeline);
      }),
    );
    const seen: unknown[] = [];
    const { result, rerender } = renderHook(
      ({ bbox }: { bbox: Bbox }) => {
        const query = useTimeline(bbox);
        seen.push(query.data);
        return query;
      },
      { initialProps: { bbox: europe }, wrapper: createQueryWrapper() },
    );
    await waitFor(() => expect(result.current.data).toEqual(otherTimeline));
    rerender({ bbox: pacific });
    await waitFor(() => expect(result.current.isFetching).toBe(true));
    expect(result.current.data).toEqual(otherTimeline);
    expect(result.current.isPlaceholderData).toBe(true);
    rerender({ bbox: { ...europe } });
    await waitFor(() => expect(result.current.isPlaceholderData).toBe(false));
    await sleep(200);
    expect(result.current.data).toEqual(otherTimeline);
    expect(seen).not.toContainEqual(timeline);
  });

  it("does not refetch for an equal bbox object", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/stats/timeline", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(timeline);
      }),
    );
    const { result, rerender } = renderHook(({ bbox }: { bbox: Bbox }) => useTimeline(bbox), {
      initialProps: { bbox: { ...europe } },
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    rerender({ bbox: { ...europe } });
    await sleep(50);
    expect(rec.requests).toHaveLength(1);
  });
});

describe("useNearby", () => {
  it("is disabled and sends nothing for a null query", async () => {
    const { result } = renderHook(() => useNearby(null), { wrapper: createQueryWrapper() });
    await sleep(50);
    expect(result.current.isPending).toBe(true);
    expect(result.current.fetchStatus).toBe("idle");
    expect(result.current.data).toBeUndefined();
  });

  it("loads events around a point with lat, lon and radius in metres", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/places/nearby", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(eventsPage);
      }),
    );
    const { result } = renderHook(() => useNearby({ lat: 41.8955, lon: 12.4823, radius: 5000 }), {
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.data).toEqual(eventsPage));
    const q = rec.requests[0]?.url.searchParams;
    expect(Number(q?.get("lat"))).toBe(41.8955);
    expect(Number(q?.get("lon"))).toBe(12.4823);
    expect(q?.get("radius")).toBe("5000");
  });

  it("exposes a 400 problem as an ApiError whose message is the detail", async () => {
    server.use(
      http.get("*/api/places/nearby", () =>
        HttpResponse.json(
          { title: "Bad Request", status: 400, detail: "radius must be between 1 and 500000" },
          { status: 400, headers: problemJson },
        ),
      ),
    );
    const { result } = renderHook(() => useNearby({ lat: 0, lon: 0, radius: 900000 }), {
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("radius must be between 1 and 500000");
  });

  it("aborts the request and shows no data when the query becomes null", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/places/nearby", async ({ request }) => {
        rec.record(request);
        await sleep(100);
        return HttpResponse.json(eventsPage);
      }),
    );
    const initial: { q: NearbyQuery | null } = { q: { lat: -17.8, lon: 179.9, radius: 1000 } };
    const { result, rerender } = renderHook(({ q }: { q: NearbyQuery | null }) => useNearby(q), {
      initialProps: initial,
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(rec.requests).toHaveLength(1));
    rerender({ q: null });
    await waitFor(() => expect(rec.requests[0]?.signal.aborted).toBe(true));
    await sleep(150);
    expect(result.current.data).toBeUndefined();
    expect(result.current.fetchStatus).toBe("idle");
  });
});

describe("useEvent", () => {
  const detail = caesarDetails;

  it("is disabled and sends nothing for a null id", async () => {
    const { result } = renderHook(() => useEvent(null), { wrapper: createQueryWrapper() });
    await sleep(50);
    expect(result.current.isPending).toBe(true);
    expect(result.current.fetchStatus).toBe("idle");
  });

  it("loads one event by its id", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events/:id", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(detail);
      }),
    );
    const { result } = renderHook(() => useEvent("Q1048:DIED"), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.data).toEqual(detail));
    expect(rec.requests[0]?.url.pathname).toMatch(/^\/api\/events\/Q1048(%3A|:)DIED$/);
  });

  it("URL-encodes the id so it cannot change the path or add a query", async () => {
    const rec = recorder();
    server.use(
      http.get("*/api/events/*", ({ request }) => {
        rec.record(request);
        return HttpResponse.json(detail);
      }),
    );
    const { result } = renderHook(() => useEvent("a/b?c"), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(rec.requests[0]?.url.pathname).toBe("/api/events/a%2Fb%3Fc");
    expect(rec.requests[0]?.url.search).toBe("");
  });

  it("exposes a 404 problem as an ApiError whose message is the detail", async () => {
    server.use(
      http.get("*/api/events/:id", () =>
        HttpResponse.json(
          { title: "Not Found", status: 404, detail: "No event Q999999999:BORN" },
          { status: 404, headers: problemJson },
        ),
      ),
    );
    const { result } = renderHook(() => useEvent("Q999999999:BORN"), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toMatchObject({ status: 404, message: "No event Q999999999:BORN" });
  });

  it("exposes a 400 problem for a malformed id", async () => {
    server.use(
      http.get("*/api/events/:id", () =>
        HttpResponse.json(
          { title: "Bad Request", status: 400, detail: "id must be <QID>:<KIND>" },
          { status: 400, headers: problemJson },
        ),
      ),
    );
    const { result } = renderHook(() => useEvent("nonsense"), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("id must be <QID>:<KIND>");
  });

  it("never shows a slow earlier event after switching to another", async () => {
    const other = mozartDetailsNoWikipedia;
    server.use(
      http.get("*/api/events/:id", async ({ params }) => {
        if (String(params.id) === "Q1048:DIED") {
          await sleep(150);
          return HttpResponse.json(detail);
        }
        return HttpResponse.json(other);
      }),
    );
    const { result, rerender } = renderHook(({ id }: { id: string | null }) => useEvent(id), {
      initialProps: { id: "Q1048:DIED" as string | null },
      wrapper: createQueryWrapper(),
    });
    rerender({ id: "Q254:BORN" });
    await waitFor(() => expect(result.current.data).toEqual(other));
    await sleep(200);
    expect(result.current.data).toEqual(other);
  });
});

describe("useEvents with a null query", () => {
  it("stays idle and sends no request while the query is null", async () => {
    const rec = recorder();
    server.use(http.get("*/api/events", ({ request }) => {
      rec.record(request);
      return HttpResponse.json(eventsPage);
    }));
    const { result } = renderHook(() => useEvents(null), { wrapper: createQueryWrapper() });
    await sleep(50);
    expect(rec.requests).toHaveLength(0);
    expect(result.current.fetchStatus).toBe("idle");
    expect(result.current.data).toBeUndefined();
  });

  it("fetches once a query is given", async () => {
    const rec = recorder();
    server.use(http.get("*/api/events", ({ request }) => {
      rec.record(request);
      return HttpResponse.json(eventsPage);
    }));
    const { result, rerender } = renderHook(
      ({ query }: { query: { bbox: Bbox } | null }) => useEvents(query),
      { wrapper: createQueryWrapper(), initialProps: { query: null as { bbox: Bbox } | null } },
    );
    rerender({ query: { bbox: pacific } });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(rec.requests).toHaveLength(1);
    expect(rec.requests[0]?.url.searchParams.get("bbox")).toBe(PACIFIC);
    expect(result.current.data).toEqual(eventsPage);
  });
});
