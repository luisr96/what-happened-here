import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useThrottledValue } from "./useThrottledValue";

function renderThrottled<T>(initial: T, intervalMs = 250) {
  const seen: T[] = [];
  const hook = renderHook(({ v }) => {
    const value = useThrottledValue(v, intervalMs);
    seen.push(value);
    return value;
  }, { initialProps: { v: initial } });
  /** Distinct values in the order the hook returned them. */
  const delivered = () => seen.filter((v, i) => i === 0 || !Object.is(v, seen[i - 1]));
  return { ...hook, delivered };
}

describe("useThrottledValue", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("starts with the initial value", () => {
    const { result } = renderThrottled(5);
    expect(result.current).toBe(5);
  });

  it("passes the first change on immediately", () => {
    const { result, rerender } = renderThrottled(0);
    act(() => vi.advanceTimersByTime(1000));

    rerender({ v: 10 });
    act(() => vi.advanceTimersByTime(0));

    expect(result.current).toBe(10);
  });

  it("coalesces changes inside one interval and delivers the final value at its end", () => {
    const { result, rerender, delivered } = renderThrottled(0);
    act(() => vi.advanceTimersByTime(1000));

    rerender({ v: 10 });
    act(() => vi.advanceTimersByTime(50));
    rerender({ v: 20 });
    act(() => vi.advanceTimersByTime(50));
    rerender({ v: 30 });
    act(() => vi.advanceTimersByTime(100));
    expect(result.current).toBe(10);

    act(() => vi.advanceTimersByTime(60));
    expect(result.current).toBe(30);
    expect(delivered()).toEqual([0, 10, 30]);
  });

  it("delivers at most one value per interval while changes continue", () => {
    const { result, rerender, delivered } = renderThrottled(0);
    act(() => vi.advanceTimersByTime(1000));

    for (let v = 1; v <= 20; v++) {
      rerender({ v });
      act(() => vi.advanceTimersByTime(50));
    }
    act(() => vi.advanceTimersByTime(500));

    expect(result.current).toBe(20);
    // 20 changes over 1000 ms: the leading one, about one per 250 ms, and the trailing one.
    const updates = delivered().slice(1);
    expect(updates[0]).toBe(1);
    expect(updates.at(-1)).toBe(20);
    expect(updates.length).toBeGreaterThanOrEqual(3);
    expect(updates.length).toBeLessThanOrEqual(6);
  });

  it("makes no extra update when the value does not change", () => {
    const { result, rerender, delivered } = renderThrottled(7);
    rerender({ v: 7 });
    act(() => vi.advanceTimersByTime(1000));
    rerender({ v: 7 });
    act(() => vi.advanceTimersByTime(1000));

    expect(result.current).toBe(7);
    expect(delivered()).toEqual([7]);
  });

  it("makes no trailing update when the leading value was the last one", () => {
    const { rerender, delivered } = renderThrottled(0);
    act(() => vi.advanceTimersByTime(1000));

    rerender({ v: 10 });
    act(() => vi.advanceTimersByTime(1000));

    expect(delivered()).toEqual([0, 10]);
  });

  it("passes a change on immediately again once a quiet interval has passed", () => {
    const { result, rerender } = renderThrottled(0);
    act(() => vi.advanceTimersByTime(1000));
    rerender({ v: 10 });
    act(() => vi.advanceTimersByTime(1000));

    rerender({ v: 20 });
    act(() => vi.advanceTimersByTime(0));

    expect(result.current).toBe(20);
  });
});
