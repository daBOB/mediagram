/** The browser write guard, the one place every write meets it, and the body reader the writes share. */

import { describe, expect, test } from "bun:test";

import { jsonBody, refuseUnsafeBrowserWrite } from "../src/http/browser-write";
import type { PlayerRequest } from "../src/http/contracts";
import type { ByteSource } from "../src/http/stream";
import { bodiless } from "../src/response";
import { createRouter } from "../src/routes";
import { WatchState } from "../src/state/store";
import { emptyIndex } from "./index-fixture";

const HOST = "127.0.0.1:8770";
const write = (method: string, over: Partial<PlayerRequest> = {}): PlayerRequest => ({
  method, path: "/api/preload", range: null, host: HOST, origin: `http://${HOST}`,
  contentType: "application/json", body: "{}", ...over,
});

describe("the dispatcher", () => {
  // Settings and status answer 204 to anything, so a write that got past the
  // guard would be accepted rather than refused by some router further on.
  const accepting = async () => bodiless(204);
  const source: ByteSource = { stream: () => new ReadableStream({ start: (c) => c.close() }) };
  const route = createRouter({
    db: emptyIndex(), source, state: new WatchState(null), settings: accepting, status: accepting,
  });

  test.each([
    ["POST", "/api/settings/unlock"],
    ["PUT", "/api/profiles/p/progress/s"],
    ["PATCH", "/api/profiles/p/collections/c"],
    ["DELETE", "/api/profiles/p/collections/c"],
    ["POST", "/api/profiles"],
    ["POST", "/api/status/playback"],
    ["POST", "/api/preload"],
    ["DELETE", "/hls/0123456789abcdef"],
    ["POST", "/api/sets/s/transcode"],
  ])("refuses an unsafe browser %s %s before any router sees it", async (method, path) => {
    expect((await route(write(method, { path, origin: "https://elsewhere.example" }))).status).toBe(403);
    // No form can send a DELETE, so it alone is not asked for a JSON type.
    const typed = method === "DELETE" ? 204 : 415;
    expect((await route(write(method, { path, contentType: "text/plain" }))).status).toBe(typed);
  });
});

describe("refuseUnsafeBrowserWrite", () => {
  const verdict = (over: Partial<PlayerRequest>) => refuseUnsafeBrowserWrite(write("POST", over))?.status ?? null;

  test("a same-host JSON write passes", () => expect(verdict({})).toBeNull());
  test("another host is 403", () => expect(verdict({ origin: "https://elsewhere.example" })).toBe(403));
  test("an Origin that is not a URL is 403", () => expect(verdict({ origin: "not an origin" })).toBe(403));
  test("no Origin passes: a caller that is not a browser", () => expect(verdict({ origin: null })).toBeNull());
  test("a write that is not JSON is 415", () => expect(verdict({ contentType: "text/plain" })).toBe(415));
  test("a DELETE skips the type check", () => {
    expect(refuseUnsafeBrowserWrite(write("DELETE", { contentType: null }))).toBeNull();
  });
});

describe("jsonBody", () => {
  test("an object is kept", () => expect(jsonBody('{"name":"Films"}')).toEqual({ name: "Films" }));
  test.each(["[1]", "not json", "", "null", "\"text\"", null, undefined])("%p reads as no fields", (body) => {
    expect(jsonBody(body)).toBeNull();
  });
});
