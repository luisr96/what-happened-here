import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "../test/server";
import { badBboxProblem, eventsPage, problemJson } from "../test/apiFixtures";
import { ApiError, apiBaseUrl, apiGet, apiUrl } from "./client";

function capture(path: string, body: unknown = eventsPage): { url: () => URL } {
  let seen: URL | undefined;
  server.use(
    http.get(`*/api${path}`, ({ request }) => {
      seen = new URL(request.url);
      return HttpResponse.json(body as never);
    }),
  );
  return {
    url: () => {
      if (seen === undefined) throw new Error("no request was made");
      return seen;
    },
  };
}

async function rejection(p: Promise<unknown>): Promise<unknown> {
  try {
    await p;
  } catch (e) {
    return e;
  }
  throw new Error("expected the promise to reject");
}

describe("apiGet", () => {
  it("fetches /api + path and returns the parsed JSON on 200", async () => {
    const req = capture("/events");
    await expect(apiGet("/events", {})).resolves.toEqual(eventsPage);
    expect(req.url().pathname).toBe("/api/events");
  });

  it("omits undefined params and joins arrays with commas", async () => {
    const req = capture("/events");
    await apiGet("/events", { bbox: "170,-10,-170,10", from: -44, to: undefined, kinds: ["BORN", "DIED"] });
    const q = req.url().searchParams;
    expect(q.get("bbox")).toBe("170,-10,-170,10");
    expect(q.get("from")).toBe("-44");
    expect(q.has("to")).toBe(false);
    expect(q.get("kinds")).toBe("BORN,DIED");
  });

  it("throws ApiError with the problem detail on a 400 problem+json", async () => {
    server.use(http.get("*/api/events", () => HttpResponse.json(badBboxProblem, { status: 400, headers: problemJson })));
    const err = await rejection(apiGet("/events", {}));
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toBeInstanceOf(Error);
    expect(err).toMatchObject({ status: 400, message: "bbox must be minLon,minLat,maxLon,maxLat" });
  });

  it("falls back to the problem title when there is no detail", async () => {
    server.use(
      http.get("*/api/events/Q1:BORN", () =>
        HttpResponse.json({ title: "Not Found", status: 404 }, { status: 404, headers: problemJson }),
      ),
    );
    const err = await rejection(apiGet("/events/Q1:BORN", {}));
    expect(err).toMatchObject({ status: 404, message: "Not Found" });
  });

  it("uses a generic message when the error body is not JSON", async () => {
    server.use(
      http.get("*/api/events", () => new HttpResponse("<html>Bad Gateway</html>", { status: 502, headers: { "Content-Type": "text/html" } })),
    );
    const err = await rejection(apiGet("/events", {}));
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 502, message: "The server returned an error (502)" });
  });

  it("uses a generic message when the problem has neither detail nor title", async () => {
    server.use(http.get("*/api/events", () => HttpResponse.json({ status: 500 }, { status: 500, headers: problemJson })));
    const err = await rejection(apiGet("/events", {}));
    expect(err).toMatchObject({ status: 500, message: "The server returned an error (500)" });
  });

  it("throws ApiError with status 0 when the server cannot be reached", async () => {
    server.use(http.get("*/api/events", () => HttpResponse.error()));
    const err = await rejection(apiGet("/events", {}));
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 0, message: "Could not reach the server" });
  });

  it("rejects with the AbortError itself, not an ApiError, when aborted", async () => {
    server.use(
      http.get("*/api/events", async () => {
        await new Promise((r) => setTimeout(r, 200));
        return HttpResponse.json(eventsPage);
      }),
    );
    const controller = new AbortController();
    const p = apiGet("/events", {}, controller.signal);
    controller.abort();
    const err = await rejection(p);
    expect(err).not.toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ name: "AbortError" });
  });
});

describe("apiBaseUrl", () => {
  it("defaults to the page origin + /api when VITE_API_BASE_URL is unset", () => {
    expect(apiBaseUrl({})).toBe(`${window.location.origin}/api`);
    expect(apiBaseUrl({ VITE_API_BASE_URL: undefined })).toBe(`${window.location.origin}/api`);
  });

  it("defaults to the page origin + /api when VITE_API_BASE_URL is empty or whitespace", () => {
    expect(apiBaseUrl({ VITE_API_BASE_URL: "" })).toBe(`${window.location.origin}/api`);
    expect(apiBaseUrl({ VITE_API_BASE_URL: "   " })).toBe(`${window.location.origin}/api`);
  });

  it("uses an absolute https URL as given", () => {
    expect(apiBaseUrl({ VITE_API_BASE_URL: "https://what-happened-here-api.fly.dev" })).toBe(
      "https://what-happened-here-api.fly.dev",
    );
  });

  it("strips trailing slashes from the configured URL", () => {
    expect(apiBaseUrl({ VITE_API_BASE_URL: "https://x.fly.dev/" })).toBe("https://x.fly.dev");
    expect(apiBaseUrl({ VITE_API_BASE_URL: "https://x.fly.dev///" })).toBe("https://x.fly.dev");
  });

  it("accepts a plain http URL", () => {
    expect(apiBaseUrl({ VITE_API_BASE_URL: "http://localhost:8080" })).toBe("http://localhost:8080");
  });

  it.each(["fly.dev", "ftp://x", "/api", "x.fly.dev/api"])("throws naming VITE_API_BASE_URL for %s", (value) => {
    expect(() => apiBaseUrl({ VITE_API_BASE_URL: value })).toThrow(/VITE_API_BASE_URL/);
  });
});

describe("apiUrl", () => {
  it("appends the path to an absolute base without an /api segment", () => {
    const url = apiUrl("https://x.fly.dev", "/events", { bbox: "170,-10,-170,10", from: -44, to: undefined, kinds: ["BORN", "DIED"] });
    expect(url.origin).toBe("https://x.fly.dev");
    expect(url.pathname).toBe("/events");
    expect(url.searchParams.get("bbox")).toBe("170,-10,-170,10");
    expect(url.searchParams.get("from")).toBe("-44");
    expect(url.searchParams.has("to")).toBe(false);
    expect(url.searchParams.get("kinds")).toBe("BORN,DIED");
  });

  it("keeps a path prefix in the base", () => {
    const url = apiUrl(`${window.location.origin}/api`, "/events/Q1:BORN", {});
    expect(url.origin).toBe(window.location.origin);
    expect(url.pathname).toBe("/api/events/Q1:BORN");
  });
});
