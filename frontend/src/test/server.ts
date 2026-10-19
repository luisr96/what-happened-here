import { setupServer } from "msw/node";

/** The MSW server for unit tests; each test adds handlers with `server.use(...)`. */
export const server = setupServer();
