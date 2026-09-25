import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import * as state from "../public/lib/watch-state.js";

let priorFetch: PropertyDescriptor | undefined;
let requests: { url: string; method: string }[] = [];

beforeEach(async () => {
  priorFetch = Object.getOwnPropertyDescriptor(globalThis, "fetch");
  Object.defineProperty(globalThis, "fetch", {
    configurable: true,
    value: async (url: unknown, init?: RequestInit) => {
      requests.push({ url: String(url), method: init?.method ?? "GET" });
      if (String(url) === "/api/profiles") return Response.json({ profiles: [{ id: "base", name: "Base" }] });
      return Response.json({
        progress: [{ setId: "film", at: 1200, duration: 6000, updatedAt: 1 }],
        watched: [{ setId: "seen", finishedAt: 5 }],
      });
    },
  });
  await state.loadProfiles();
  await state.useProfile("base");
  requests = [];
});

afterEach(async () => {
  await state.useProfile(null);
  if (priorFetch) Object.defineProperty(globalThis, "fetch", priorFetch);
  else Reflect.deleteProperty(globalThis, "fetch");
});

describe("marking a title finished by hand", () => {
  test("drops its position and records it as watched, as reaching the end does", () => {
    state.markFinished("film");

    expect(state.progressOf("film")).toBeNull();
    expect(state.isWatched("film")).toBe(true);
    expect(requests).toEqual([
      { url: "/api/profiles/base/progress/film", method: "DELETE" },
      { url: "/api/profiles/base/watched/film", method: "PUT" },
    ]);
  });

  test("keeps the date a title was first finished", () => {
    const before = state.watchedAt("seen");

    state.markFinished("seen");

    expect(state.watchedAt("seen")).toBe(before);
    expect(requests.some((r) => r.url.endsWith("/watched/seen"))).toBe(false);
  });
});
