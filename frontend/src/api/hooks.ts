import { keepPreviousData, skipToken, useQuery } from "@tanstack/react-query";
import type { UseQueryResult } from "@tanstack/react-query";
import { apiGet, bboxParam } from "./client";
import type { ApiError, Bbox, Kind } from "./client";
import type { components } from "./schema";

type Schemas = components["schemas"];
export type EventsResponse = Schemas["EventsResponse"];
export type EventView = Schemas["EventView"];
export type EventDetails = Schemas["EventDetails"];
export type TimelineResponse = Schemas["TimelineResponse"];

export type EventsQuery = {
  readonly bbox: Bbox;
  readonly from?: number;
  readonly to?: number;
  readonly kinds?: readonly Kind[];
  readonly minImportance?: number;
  readonly limit?: number;
};

export type NearbyQuery = { readonly lat: number; readonly lon: number; readonly radius: number };

// Query keys hold plain values, so TanStack compares them by value: a new object with equal
// values is the same query and doesn't refetch. Every queryFn passes TanStack's `signal` on,
// which is what lets TanStack abort a request once nothing is waiting for it.

/** Events in the viewport; keeps showing the previous result while a new one loads. */
export function useEvents(query: EventsQuery): UseQueryResult<EventsResponse, ApiError> {
  const params = {
    bbox: bboxParam(query.bbox),
    from: query.from,
    to: query.to,
    kinds: query.kinds,
    minImportance: query.minImportance,
    limit: query.limit,
  };
  return useQuery({
    queryKey: ["events", params],
    queryFn: ({ signal }) => apiGet<EventsResponse>("/events", params, signal),
    placeholderData: keepPreviousData,
  });
}

/** Per-decade counts for the time bar; keeps the previous counts while new ones load. */
export function useTimeline(bbox: Bbox): UseQueryResult<TimelineResponse, ApiError> {
  const params = { bbox: bboxParam(bbox) };
  return useQuery({
    queryKey: ["timeline", params],
    queryFn: ({ signal }) => apiGet<TimelineResponse>("/stats/timeline", params, signal),
    placeholderData: keepPreviousData,
  });
}

/** "What happened here" around a clicked point; idle (no request) while `query` is null. */
export function useNearby(query: NearbyQuery | null): UseQueryResult<EventsResponse, ApiError> {
  return useQuery({
    queryKey: ["nearby", query],
    queryFn: query === null
      ? skipToken
      : ({ signal }) => apiGet<EventsResponse>("/places/nearby", query, signal),
  });
}

/** One event with its Wikipedia summary; idle (no request) while `id` is null. */
export function useEvent(id: string | null): UseQueryResult<EventDetails, ApiError> {
  return useQuery({
    queryKey: ["event", id],
    queryFn: id === null
      ? skipToken
      : ({ signal }) => apiGet<EventDetails>(`/events/${encodeURIComponent(id)}`, {}, signal),
  });
}
