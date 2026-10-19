import { describe, expect, it } from "vitest";
import { viewportToBbox } from "./bbox";

describe("viewportToBbox", () => {
  it("passes an ordinary view through unchanged", () => {
    expect(viewportToBbox({ west: -10, south: 35, east: 30, north: 60 })).toEqual({
      minLon: -10, minLat: 35, maxLon: 30, maxLat: 60,
    });
  });

  it("wraps longitudes beyond 180 back into range", () => {
    expect(viewportToBbox({ west: 190, south: 0, east: 200, north: 10 })).toEqual({
      minLon: -170, minLat: 0, maxLon: -160, maxLat: 10,
    });
  });

  it("wraps longitudes below -180 back into range", () => {
    expect(viewportToBbox({ west: -200, south: 0, east: -190, north: 10 })).toEqual({
      minLon: 160, minLat: 0, maxLon: 170, maxLat: 10,
    });
  });

  it("gives minLon > maxLon for a view crossing the antimeridian eastwards", () => {
    expect(viewportToBbox({ west: 170, south: -10, east: 190, north: 10 })).toEqual({
      minLon: 170, minLat: -10, maxLon: -170, maxLat: 10,
    });
  });

  it("gives minLon > maxLon for a view crossing the antimeridian westwards", () => {
    expect(viewportToBbox({ west: -190, south: -10, east: -170, north: 10 })).toEqual({
      minLon: 170, minLat: -10, maxLon: -170, maxLat: 10,
    });
  });

  it("covers the whole world for a view exactly 360 degrees wide", () => {
    expect(viewportToBbox({ west: -180, south: -60, east: 180, north: 60 })).toEqual({
      minLon: -180, minLat: -60, maxLon: 180, maxLat: 60,
    });
  });

  it("covers the whole world, not a crossing box, for a view wider than 360 degrees", () => {
    expect(viewportToBbox({ west: -400, south: -60, east: 300, north: 60 })).toMatchObject({ minLon: -180, maxLon: 180 });
    expect(viewportToBbox({ west: 10, south: -60, east: 370, north: 60 })).toMatchObject({ minLon: -180, maxLon: 180 });
  });

  it("clamps latitudes to [-90, 90]", () => {
    expect(viewportToBbox({ west: 0, south: -95, east: 10, north: 100 })).toMatchObject({ minLat: -90, maxLat: 90 });
  });

  it("keeps an east edge of exactly 180 as 180 when not crossing", () => {
    expect(viewportToBbox({ west: 170, south: 0, east: 180, north: 10 })).toMatchObject({ minLon: 170, maxLon: 180 });
  });

  it("keeps a west edge of exactly -180 as -180", () => {
    expect(viewportToBbox({ west: -180, south: 0, east: -170, north: 10 })).toMatchObject({ minLon: -180, maxLon: -170 });
  });
});
