import { describe, expect, it } from "vitest";
import { formatDistance } from "./nearby";

const MI = 1609.344;
const FT = 0.3048;

describe("formatDistance in kilometres", () => {
  it("defaults to km and keeps the km rules", () => {
    expect(formatDistance(800)).toBe("800 m");
    expect(formatDistance(2293)).toBe("2.3 km");
    expect(formatDistance(12345)).toBe("12 km");
  });

  it("formats the same when km is passed explicitly", () => {
    expect(formatDistance(800, "km")).toBe("800 m");
    expect(formatDistance(2293, "km")).toBe("2.3 km");
    expect(formatDistance(500000, "km")).toBe("500 km");
  });
});

describe("formatDistance in miles", () => {
  it("shows feet rounded to the nearest 10 below 0.1 mi", () => {
    expect(formatDistance(520 * FT, "mi")).toBe("520 ft");
    expect(formatDistance(100, "mi")).toBe("330 ft");
  });

  it("switches to miles at 0.1 mi", () => {
    expect(formatDistance(0.1 * MI + 0.5, "mi")).toBe("0.1 mi");
  });

  it("shows one decimal below 10 mi", () => {
    expect(formatDistance(2293, "mi")).toBe("1.4 mi");
    expect(formatDistance(2.3 * MI, "mi")).toBe("2.3 mi");
    expect(formatDistance(9.9 * MI, "mi")).toBe("9.9 mi");
  });

  it("shows whole miles from 10 mi", () => {
    expect(formatDistance(12 * MI, "mi")).toBe("12 mi");
    expect(formatDistance(500000, "mi")).toBe("311 mi");
  });
});
