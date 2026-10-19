import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "./client";

/**
 * Retry only what can fix itself: network failures (status 0) and 5xx, at most twice. A 4xx
 * would fail the same way again, and anything else (an abort, a programmer error) isn't retried.
 */
export function shouldRetry(failures: number, error: unknown): boolean {
  return failures < 2 && error instanceof ApiError && (error.status === 0 || error.status >= 500);
}

export function createQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: shouldRetry } } });
}
