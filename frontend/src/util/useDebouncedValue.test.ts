import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useDebouncedValue } from "./useDebouncedValue";

describe("useDebouncedValue", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("starts with the initial value", () => {
    const { result } = renderHook(() => useDebouncedValue(5, 250));
    expect(result.current).toBe(5);
  });

  it("passes on only the last of quick changes, once they stop", () => {
    const { result, rerender } = renderHook(({ v }) => useDebouncedValue(v, 250), { initialProps: { v: 0 } });
    rerender({ v: 10 });
    act(() => vi.advanceTimersByTime(100));
    rerender({ v: 20 });
    act(() => vi.advanceTimersByTime(200));
    expect(result.current).toBe(0);
    act(() => vi.advanceTimersByTime(50));
    expect(result.current).toBe(20);
  });
});
