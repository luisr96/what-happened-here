import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DEFAULT_SETTINGS, MAP_STYLES, styleUrl, useSettings } from "./settings";

const KEY = "whh.settings";

beforeEach(() => localStorage.clear());
afterEach(() => vi.restoreAllMocks());

function stored(): unknown {
  const raw = localStorage.getItem(KEY);
  return raw === null ? null : JSON.parse(raw);
}

describe("settings constants", () => {
  it("defaults to kilometres and the liberty style", () => {
    expect(DEFAULT_SETTINGS).toEqual({ units: "km", mapStyle: "liberty" });
  });

  it("lists the five OpenFreeMap styles with their labels, in order", () => {
    expect(MAP_STYLES.map((s) => [s.id, s.label])).toEqual([
      ["dark", "Dark"],
      ["positron", "Positron"],
      ["liberty", "Liberty"],
      ["bright", "Bright"],
      ["fiord", "Fiord"],
    ]);
  });

  it.each(["dark", "positron", "liberty", "bright", "fiord"] as const)("builds the OpenFreeMap URL for %s", (id) => {
    expect(styleUrl(id)).toBe(`https://tiles.openfreemap.org/styles/${id}`);
  });
});

describe("useSettings", () => {
  it("returns the defaults when nothing is stored", () => {
    const { result } = renderHook(() => useSettings());
    expect(result.current[0]).toEqual(DEFAULT_SETTINGS);
  });

  it("reads stored settings on the first render", () => {
    localStorage.setItem(KEY, JSON.stringify({ units: "mi", mapStyle: "fiord" }));
    const { result } = renderHook(() => useSettings());
    expect(result.current[0]).toEqual({ units: "mi", mapStyle: "fiord" });
  });

  it("applies and stores a change as JSON", () => {
    const { result } = renderHook(() => useSettings());
    act(() => result.current[1]({ units: "mi", mapStyle: "positron" }));
    expect(result.current[0]).toEqual({ units: "mi", mapStyle: "positron" });
    expect(stored()).toEqual({ units: "mi", mapStyle: "positron" });
  });

  it("survives a remount", () => {
    const first = renderHook(() => useSettings());
    act(() => first.result.current[1]({ units: "mi", mapStyle: "bright" }));
    first.unmount();
    const second = renderHook(() => useSettings());
    expect(second.result.current[0]).toEqual({ units: "mi", mapStyle: "bright" });
  });

  it("falls back to the defaults for unparseable JSON", () => {
    localStorage.setItem(KEY, "{not json");
    const { result } = renderHook(() => useSettings());
    expect(result.current[0]).toEqual(DEFAULT_SETTINGS);
  });

  it.each(["null", "42", "\"mi\"", "[\"mi\",\"positron\"]"])("falls back to the defaults for non-object JSON %s", (raw) => {
    localStorage.setItem(KEY, raw);
    const { result } = renderHook(() => useSettings());
    expect(result.current[0]).toEqual(DEFAULT_SETTINGS);
  });

  it("fills a missing field from the defaults", () => {
    localStorage.setItem(KEY, JSON.stringify({ units: "mi" }));
    const { result } = renderHook(() => useSettings());
    expect(result.current[0]).toEqual({ units: "mi", mapStyle: "liberty" });
  });

  it("replaces only the invalid field with its default", () => {
    localStorage.setItem(KEY, JSON.stringify({ units: "yards", mapStyle: "positron" }));
    const { result } = renderHook(() => useSettings());
    expect(result.current[0]).toEqual({ units: "km", mapStyle: "positron" });
  });

  it("rejects an unknown map style and a wrongly typed field", () => {
    localStorage.setItem(KEY, JSON.stringify({ units: "mi", mapStyle: "satellite" }));
    expect(renderHook(() => useSettings()).result.current[0]).toEqual({ units: "mi", mapStyle: "liberty" });
    localStorage.setItem(KEY, JSON.stringify({ units: 1, mapStyle: ["liberty"] }));
    expect(renderHook(() => useSettings()).result.current[0]).toEqual(DEFAULT_SETTINGS);
  });

  it("uses the defaults when localStorage throws on read", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("SecurityError");
    });
    const { result } = renderHook(() => useSettings());
    expect(result.current[0]).toEqual(DEFAULT_SETTINGS);
  });

  it("still applies a change in memory when localStorage throws on write", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("QuotaExceededError");
    });
    const { result } = renderHook(() => useSettings());
    act(() => result.current[1]({ units: "mi", mapStyle: "liberty" }));
    expect(result.current[0]).toEqual({ units: "mi", mapStyle: "liberty" });
  });
});
