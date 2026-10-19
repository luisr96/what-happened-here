import type { components } from "../api/schema";

type EventView = components["schemas"]["EventView"];
type EventsResponse = components["schemas"]["EventsResponse"];
type TimelineResponse = components["schemas"]["TimelineResponse"];
type ProblemDetail = components["schemas"]["ProblemDetail"];
type EventDetails = components["schemas"]["EventDetails"];

/** Caesar's death, 44 BC, day precision, Julian calendar, exact coordinates. */
export const caesarDied: EventView = {
  id: "Q1048:DIED",
  itemId: "Q1048",
  kind: "DIED",
  label: "Julius Caesar",
  importance: 250,
  when: { year: -44, month: 3, day: 15, precision: "DAY", calendar: "JULIAN" },
  end: null,
  earliestYear: -44,
  latestYear: -44,
  latitude: 41.8955,
  longitude: 12.4823,
  coordPrecisionDeg: 0.0001,
  approximate: false,
  placeQid: "Q220",
};

/** Lisbon 1755, a 1-degree (approximate) point at sea, single-year range. */
export const lisbonEarthquake: EventView = {
  id: "Q189040:HAPPENED",
  itemId: "Q189040",
  kind: "HAPPENED",
  label: "1755 Lisbon earthquake",
  importance: 80,
  when: { year: 1755, month: 11, day: 1, precision: "DAY", calendar: "GREGORIAN" },
  end: null,
  earliestYear: 1755,
  latestYear: 1755,
  latitude: 36,
  longitude: -11,
  coordPrecisionDeg: 1,
  approximate: true,
  placeQid: null,
};

/** A century-precision founding near the antimeridian (Fiji), precision unknown. */
export const fijiFounded: EventView = {
  id: "Q123:FOUNDED",
  itemId: "Q123",
  kind: "FOUNDED",
  label: "Some village",
  importance: 2,
  when: { year: 1200, month: 0, day: 0, precision: "CENTURY", calendar: "GREGORIAN" },
  end: null,
  earliestYear: 1101,
  latestYear: 1200,
  latitude: -17.8,
  longitude: 179.9,
  coordPrecisionDeg: null,
  approximate: true,
  placeQid: null,
};

/** An unlabelled item (label null). */
export const mozartBorn: EventView = {
  id: "Q254:BORN",
  itemId: "Q254",
  kind: "BORN",
  label: null,
  importance: 200,
  when: { year: 1756, month: 1, day: 27, precision: "DAY", calendar: "GREGORIAN" },
  end: null,
  earliestYear: 1756,
  latestYear: 1756,
  latitude: 47.8,
  longitude: 13.04,
  coordPrecisionDeg: null,
  approximate: true,
  placeQid: "Q34713",
};

export const caesarDetails: EventDetails = {
  event: caesarDied,
  wikipedia: {
    title: "Julius Caesar",
    extract: "Gaius Julius Caesar was a Roman general and statesman.",
    url: "https://en.wikipedia.org/wiki/Julius_Caesar",
    thumbnailUrl: null,
  },
};

/** Details of an event whose item has no English Wikipedia article. */
export const mozartDetailsNoWikipedia: EventDetails = { event: mozartBorn, wikipedia: null };

export const eventsPage: EventsResponse = { events: [caesarDied, lisbonEarthquake], truncated: false };
export const otherEventsPage: EventsResponse = { events: [fijiFounded], truncated: true };
export const emptyEvents: EventsResponse = { events: [], truncated: false };

export const timeline: TimelineResponse = {
  decades: [
    { decade: -50, count: 3 },
    { decade: 1750, count: 12 },
  ],
};
export const otherTimeline: TimelineResponse = { decades: [{ decade: 1200, count: 1 }] };

export const badBboxProblem: ProblemDetail = {
  type: "about:blank",
  title: "Bad Request",
  status: 400,
  detail: "bbox must be minLon,minLat,maxLon,maxLat",
  instance: "/events",
};

export const problemJson = { "Content-Type": "application/problem+json" } as const;
