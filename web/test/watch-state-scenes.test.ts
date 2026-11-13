import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import * as state from "../public/lib/watch-state.js";

let priorFetch: PropertyDescriptor | undefined;
let calls: { url: string; method: string; body?: string }[] = [];
let snapshot: unknown;

beforeEach(async () => {
  priorFetch = Object.getOwnPropertyDescriptor(globalThis, "fetch");
  calls = [];
  snapshot = { scenes: [{ id: "s0", setId: "a", at: 5, markedAt: 1 }] };
  Object.defineProperty(globalThis, "fetch", {
    configurable: true,
    value: async (url: unknown, init?: RequestInit) => {
      const call = { url: String(url), method: init?.method ?? "GET", body: init?.body as string | undefined };
      calls.push(call);
      if (call.url === "/api/profiles") return Response.json({ profiles: [{ id: "base", name: "Base" }] });
      if (call.method === "POST") return Response.json({ id: "s1" }, { status: 201 });
      if (call.method === "DELETE") return new Response(null, { status: 204 });
      return Response.json(snapshot);
    },
  });
  await state.loadProfiles();
  await state.useProfile("base");
});

afterEach(async () => {
  await state.useProfile(null);
  if (priorFetch) Object.defineProperty(globalThis, "fetch", priorFetch);
  else Reflect.deleteProperty(globalThis, "fetch");
});

describe("scene marks", () => {
  test("markScene posts and holds the new mark", async () => {
    expect(await state.markScene("b", 3735)).toBe("s1");
    const post = calls.find((c) => c.method === "POST")!;
    expect(post.url).toBe("/api/profiles/base/scenes");
    expect(JSON.parse(post.body!)).toEqual({ setId: "b", at: 3735 });
    expect(state.scenes().map((s) => s.id)).toEqual(["s1", "s0"]);
  });

  test("a mark the server refused is not held", async () => {
    Object.defineProperty(globalThis, "fetch", { configurable: true, value: async () => new Response(null, { status: 404 }) });
    expect(await state.markScene("b", 1)).toBeNull();
    expect(state.scenes().map((s) => s.id)).toEqual(["s0"]);
  });

  test("removeScene drops it locally and sends DELETE", () => {
    state.removeScene("s0");
    expect(state.scenes()).toEqual([]);
    expect(calls.some((c) => c.method === "DELETE" && c.url === "/api/profiles/base/scenes/s0")).toBe(true);
  });

  test("a refreshed snapshot replaces the held scenes", async () => {
    snapshot = { scenes: [{ id: "s9", setId: "z", at: 1, markedAt: 2 }] };
    await state.refreshState();
    expect(state.scenes().map((s) => s.id)).toEqual(["s9"]);
  });
});
