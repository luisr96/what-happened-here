import { describe, expect, it } from "vitest";
import { clickRadiusMetres, formatDistance } from "./nearby";

describe("clickRadiusMetres", () => {
  it("clamps to 500000 at zoom 0 on the equator (raw ~2.35 million m)", () => {
    expect(clickRadiusMetres(0, 0)).toBe(500000);
  });

  it("covers 30 px at zoom 10 on the equator as 2293 m", () => {
    expect(clickRadiusMetres(0, 10)).toBe(2293);
  });

  it("halves the distance at 60 degrees latitude", () => {
    expect(Math.abs(clickRadiusMetres(60, 10) - 1147)).toBeLessThanOrEqual(1);
  });

  it("treats southern latitudes like northern ones", () => {
    expect(clickRadiusMetres(-60, 10)).toBe(clickRadiusMetres(60, 10));
  });

  it("scales with the pixel count", () => {
    expect(Math.abs(clickRadiusMetres(0, 10, 60) - 4586)).toBeLessThanOrEqual(1);
  });

  it("supports fractional zoom", () => {
    const r = clickRadiusMetres(0, 10.5);
    expect(Math.abs(r - Math.round(2293 / Math.SQRT2))).toBeLessThanOrEqual(1);
  });

  it("returns whole metres", () => {
    expect(Number.isInteger(clickRadiusMetres(41.9, 13.3))).toBe(true);
  });

  it("never goes below 1 m, even at the pole or very deep zoom", () => {
    expect(clickRadiusMetres(90, 10)).toBe(1);
    expect(clickRadiusMetres(0, 24)).toBe(1);
  });
});

describe("formatDistance", () => {
  it("shows metres below 1 km", () => {
    expect(formatDistance(800)).toBe("800 m");
    expect(formatDistance(1)).toBe("1 m");
  });

  it("shows one decimal between 1 and 10 km", () => {
    expect(formatDistance(2293)).toBe("2.3 km");
    expect(formatDistance(1000)).toBe("1.0 km");
  });

  it("shows whole kilometres above 10 km", () => {
    expect(formatDistance(12345)).toBe("12 km");
    expect(formatDistance(500000)).toBe("500 km");
  });
});
