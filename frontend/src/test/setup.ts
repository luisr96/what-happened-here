import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, beforeEach } from "vitest";
import { ABOUT_SEEN_KEY } from "../about/aboutSeen";
import { server } from "./server";

// Every test talks to the API through MSW; an unhandled request is a test bug, so it fails.
beforeAll(() => server.listen({ onUnhandledFrame: "error" }));
// The About dialog opens on a first visit; tests not about it start as returning visitors.
beforeEach(() => localStorage.setItem(ABOUT_SEEN_KEY, "1"));
afterEach(() => {
  cleanup();
  server.resetHandlers();
});
afterAll(() => server.close());
