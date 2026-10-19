import { describe, expect, it } from "vitest";
import { ApiError } from "./client";
import { shouldRetry } from "./queryClient";

describe("shouldRetry", () => {
  it.each([0, 500, 502, 503])("retries status %i up to twice", (status) => {
    const error = new ApiError(status, "failed");
    expect(shouldRetry(0, error)).toBe(true);
    expect(shouldRetry(1, error)).toBe(true);
    expect(shouldRetry(2, error)).toBe(false);
  });

  it.each([400, 404, 422])("never retries client error %i", (status) => {
    expect(shouldRetry(0, new ApiError(status, "bad request"))).toBe(false);
  });

  it("never retries an abort or a programmer error", () => {
    expect(shouldRetry(0, new DOMException("aborted", "AbortError"))).toBe(false);
    expect(shouldRetry(0, new TypeError("x is undefined"))).toBe(false);
  });
});
