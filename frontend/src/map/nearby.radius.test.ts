import { describe, expect, it } from "vitest";
import { clampRadius, distanceMetres, edgePoint } from "./nearby";

const paris = { lat: 48.8566, lon: 2.3522 };
const london = { lat: 51.5074, lon: -0.1278 };

describe("distanceMetres", () => {
  it("measures Paris to London as about 343.5 km along the great circle", () => {
    expect(Math.abs(distanceMetres(paris, london) - 343500) / 343500).toBeLessThan(0.01);
  });

  it("is symmetric", () => {
    expect(distanceMetres(london, paris)).toBeCloseTo(distanceMetres(paris, london), 6);
  });

  it("is 0 for the same point", () => {
    expect(distanceMetres(paris, paris)).toBe(0);
  });

  it("goes the short way across the antimeridian (~111 km, not ~40,000 km)", () => {
    const d = distanceMetres({ lat: 0, lon: 179.5 }, { lat: 0, lon: -179.5 });
    expect(Math.abs(d - 111195) / 111195).toBeLessThan(0.005);
  });

  it("uses R = 6371008.8 m: 1 degree along the equator is ~111,195 m", () => {
    expect(distanceMetres({ lat: 0, lon: 0 }, { lat: 0, lon: 1 })).toBeCloseTo(111195.08, 0);
  });

  it("measures pole to pole as half the circumference", () => {
    expect(distanceMetres({ lat: 90, lon: 0 }, { lat: -90, lon: 0 })).toBeCloseTo(Math.PI * 6371008.8, -1);
  });
});

describe("edgePoint", () => {
  it.each([
    [0, 1000],
    [0, 100000],
    [60, 2293],
    [60, 100000],
    [-60, 50000],
  ])("lies about radius due east of the centre at lat %d, radius %d m", (lat, radius) => {
    const centre = { lat, lon: 12.5 };
    const edge = edgePoint({ ...centre, radius });
    expect(edge.lat).toBe(lat);
    expect(edge.lon).toBeGreaterThan(12.5);
    expect(Math.abs(distanceMetres(centre, edge) - radius) / radius).toBeLessThan(0.01);
  });

  it("wraps past the antimeridian into -180..180", () => {
    const edge = edgePoint({ lat: -17.8, lon: 179.9, radius: 50000 });
    expect(edge.lon).toBeGreaterThanOrEqual(-180);
    expect(edge.lon).toBeLessThanOrEqual(180);
    expect(edge.lon).toBeLessThan(0);
    expect(Math.abs(distanceMetres({ lat: -17.8, lon: 179.9 }, edge) - 50000) / 50000).toBeLessThan(0.01);
  });
});

describe("clampRadius", () => {
  it("rounds to whole metres", () => {
    expect(clampRadius(2293.4)).toBe(2293);
    expect(clampRadius(2293.6)).toBe(2294);
  });

  it("keeps the API's minimum of 1 m", () => {
    expect(clampRadius(0)).toBe(1);
    expect(clampRadius(0.2)).toBe(1);
    expect(clampRadius(-5)).toBe(1);
  });

  it("keeps the API's maximum of 500000 m", () => {
    expect(clampRadius(500000)).toBe(500000);
    expect(clampRadius(2_000_000)).toBe(500000);
  });
});
