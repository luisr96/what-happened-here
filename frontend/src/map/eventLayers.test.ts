import { ScatterplotLayer } from "@deck.gl/layers";
import { describe, expect, it } from "vitest";
import type { EventView } from "../api/hooks";
import { caesarDied, fijiFounded, lisbonEarthquake, mozartBorn } from "../test/apiFixtures";
import { eventLayers } from "./eventLayers";
import { kindColor } from "./kinds";

const all: readonly EventView[] = [caesarDied, lisbonEarthquake, fijiFounded, mozartBorn];

function layers(events: readonly EventView[] = all) {
  const result = eventLayers(events);
  expect(result).toHaveLength(2);
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
