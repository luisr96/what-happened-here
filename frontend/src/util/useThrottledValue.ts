import { useEffect, useRef, useState } from "react";

/**
 * `value`, updated at most once per `intervalMs`: the first change after a quiet spell passes at
 * once, later ones are held and only the latest is passed when the interval is up. Unlike a
 * debounce it keeps updating during a continuous change (e.g. dragging the time range).
 */
export function useThrottledValue<T>(value: T, intervalMs: number): T {
  const [throttled, setThrottled] = useState(value);
  const lastPassed = useRef(Number.NEGATIVE_INFINITY);
  const passedValue = useRef(value);

  useEffect(() => {
    // Nothing new (e.g. the first render): leave the interval clock alone.
    if (Object.is(value, passedValue.current)) return;
    const pass = () => {
      lastPassed.current = Date.now();
      passedValue.current = value;
      setThrottled(value);
    };
    const wait = lastPassed.current + intervalMs - Date.now();
    if (wait <= 0) {
      pass();
      return;
    }
    const timer = setTimeout(pass, wait);
    return () => clearTimeout(timer);
  }, [value, intervalMs]);

  return throttled;
}
