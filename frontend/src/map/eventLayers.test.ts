import { ScatterplotLayer } from "@deck.gl/layers";
import { describe, expect, it } from "vitest";
import type { EventView } from "../api/hooks";
import { caesarDied, fijiFounded, lisbonEarthquake, mozartBorn } from "../test/apiFixtures";
import { eventLayers, selectionLayer } from "./eventLayers";
import { kindColor } from "./kinds";

const all: readonly EventView[] = [caesarDied, lisbonEarthquake, fijiFounded, mozartBorn];

function layers(events: readonly EventView[] = all) {
  const result = eventLayers(events);
  expect(result.length).toBeGreaterThanOrEqual(2);
  const [halo, exact] = result;
  if (!(halo instanceof ScatterplotLayer) || !(exact instanceof ScatterplotLayer)) {
    throw new Error("expected two ScatterplotLayers");
  }
  return { halo, exact };
}

function accessor<T>(layer: ScatterplotLayer, name: "getPosition" | "getFillColor" | "getRadius"): (d: EventView) => T {
  const fn: unknown = layer.props[name];
  if (typeof fn !== "function") throw new Error(`${name} is not an accessor function`);
  return fn as (d: EventView) => T;
}

const withImportance = (importance: number): EventView => ({ ...caesarDied, importance });

describe("eventLayers", () => {
  it("draws approximate halos first and exact points on top", () => {
    const { halo, exact } = layers();
    expect(halo.id).toBe("events-approximate");
    expect(exact.id).toBe("events-exact");
  });

  it("splits events by the approximate flag", () => {
    const { halo, exact } = layers();
    expect(halo.props.data).toEqual([lisbonEarthquake, fijiFounded, mozartBorn]);
    expect(exact.props.data).toEqual([caesarDied]);
  });

  it("returns both layers, empty, when there are no events", () => {
    const { halo, exact } = layers([]);
    expect(halo.props.data).toEqual([]);
    expect(exact.props.data).toEqual([]);
  });

  it("makes both layers pickable for tooltips", () => {
    const { halo, exact } = layers();
    expect(halo.props.pickable).toBe(true);
    expect(exact.props.pickable).toBe(true);
  });

  it("positions points at [longitude, latitude], including near the antimeridian", () => {
    const { halo, exact } = layers();
    expect(accessor(exact, "getPosition")(caesarDied)).toEqual([12.4823, 41.8955]);
    expect(accessor(halo, "getPosition")(fijiFounded)).toEqual([179.9, -17.8]);
  });

  it("colours by kind with a lower alpha for halos than for exact points", () => {
    const { halo, exact } = layers();
    const exactColor = accessor<number[]>(exact, "getFillColor")(caesarDied);
    const haloColor = accessor<number[]>(halo, "getFillColor")(lisbonEarthquake);
    expect(exactColor).toHaveLength(4);
    expect(haloColor).toHaveLength(4);
    expect(exactColor.slice(0, 3)).toEqual([...kindColor("DIED")]);
    expect(haloColor.slice(0, 3)).toEqual([...kindColor("HAPPENED")]);
    expect(haloColor[3]).toBeLessThan(exactColor[3] ?? 0);
  });

  it("sizes points in pixels, growing with importance, at least 2 px and capped", () => {
    for (const layer of Object.values(layers())) {
      expect(layer.props.radiusUnits).toBe("pixels");
      const r = accessor<number>(layer, "getRadius");
      expect(r(withImportance(1))).toBeLessThan(r(withImportance(100)));
      expect(r(withImportance(0))).toBeGreaterThanOrEqual(2);
      expect(r(withImportance(1))).toBeGreaterThanOrEqual(2);
      expect(r(withImportance(10000))).toBe(r(withImportance(1000)));
      expect(Number.isFinite(r(withImportance(10000)))).toBe(true);
    }
  });

  it("draws a halo larger than the exact point for the same importance", () => {
    const { halo, exact } = layers();
    for (const importance of [1, 50, 1000]) {
      expect(accessor<number>(halo, "getRadius")(withImportance(importance)))
        .toBeGreaterThan(accessor<number>(exact, "getRadius")(withImportance(importance)));
    }
  });
});

describe("eventLayers selection ring", () => {
  function withSelection(events: readonly EventView[], selectedId?: string | null) {
    const result = selectedId === undefined ? eventLayers(events) : eventLayers(events, selectedId);
    expect(result).toHaveLength(3);
    const ring = result[2];
    if (!(ring instanceof ScatterplotLayer)) throw new Error("expected a ScatterplotLayer");
    return { ring, result };
  }

  it("adds a third layer, events-selected, drawn after the halos and points", () => {
    const { ring, result } = withSelection(all, caesarDied.id);
    expect(result[0]?.id).toBe("events-approximate");
    expect(result[1]?.id).toBe("events-exact");
    expect(ring.id).toBe("events-selected");
  });

  it("contains only the selected event, exact or approximate", () => {
    expect(withSelection(all, caesarDied.id).ring.props.data).toEqual([caesarDied]);
    expect(withSelection(all, fijiFounded.id).ring.props.data).toEqual([fijiFounded]);
  });

  it("is empty when nothing is selected", () => {
    expect(withSelection(all).ring.props.data).toEqual([]);
    expect(withSelection(all, null).ring.props.data).toEqual([]);
  });

  it("is empty when the selected event is not among the events", () => {
    expect(withSelection([lisbonEarthquake], caesarDied.id).ring.props.data).toEqual([]);
  });

  it("draws an unfilled, stroked ring that is not pickable", () => {
    const { ring } = withSelection(all, caesarDied.id);
    expect(ring.props.stroked).toBe(true);
    expect(ring.props.filled).toBe(false);
    expect(ring.props.pickable).toBe(false);
  });

  it("keeps the halo and point layers unchanged when an event is selected", () => {
    const { result } = withSelection(all, caesarDied.id);
    expect(result[0]?.props.data).toEqual([lisbonEarthquake, fijiFounded, mozartBorn]);
    expect(result[1]?.props.data).toEqual([caesarDied]);
    expect(result[0]?.props.pickable).toBe(true);
    expect(result[1]?.props.pickable).toBe(true);
  });

  it("positions the ring at the selected event, near the antimeridian too", () => {
    const { ring } = withSelection(all, fijiFounded.id);
    expect(accessor(ring, "getPosition")(fijiFounded)).toEqual([179.9, -17.8]);
  });
});

describe("selectionLayer", () => {
  it("rings the given event even when it's no longer among the loaded events", () => {
    const ring = selectionLayer(caesarDied);
    expect(ring.props.data).toEqual([caesarDied]);
    expect(selectionLayer(null).props.data).toEqual([]);
  });
});
