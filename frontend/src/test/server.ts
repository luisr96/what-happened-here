import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { statsSummary } from "./apiFixtures";

/** Handlers every test starts with; `server.resetHandlers()` returns to these. */
export const defaultHandlers = [http.get("*/api/stats/summary", () => HttpResponse.json(statsSummary))];

/** The MSW server for unit tests; each test adds handlers with `server.use(...)`. */
export const server = setupServer(...defaultHandlers);
