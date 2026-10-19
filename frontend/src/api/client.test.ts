import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "../test/server";
import { badBboxProblem, eventsPage, problemJson } from "../test/apiFixtures";
import { ApiError, apiGet } from "./client";

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
