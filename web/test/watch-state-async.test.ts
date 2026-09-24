import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import * as state from "../public/lib/watch-state.js";

let priorFetch: PropertyDescriptor | undefined;

function serve(handler: (url: unknown, init?: RequestInit) => Promise<Response>) {
  Object.defineProperty(globalThis, "fetch", { configurable: true, value: handler });
}

const answer = (name: string) => Response.json({
  watchlist: [name],
  progress: [{ setId: name, at: 30, duration: 60, updatedAt: 1 }],
});

beforeEach(async () => {
  priorFetch = Object.getOwnPropertyDescriptor(globalThis, "fetch");
  serve(async (url) => String(url) === "/api/profiles"
    ? Response.json({ profiles: [{ id: "base", name: "Base" }] })
    : answer("base"));
  await state.loadProfiles();
  await state.useProfile("base");
});

afterEach(async () => {
  await state.useProfile(null);
  if (priorFetch) Object.defineProperty(globalThis, "fetch", priorFetch);
  else Reflect.deleteProperty(globalThis, "fetch");
});

describe("profile selection responses", () => {
  test("an older profile cannot replace the selected profile's state", async () => {
    const first = Promise.withResolvers<Response>();
    const second = Promise.withResolvers<Response>();
    serve((url) => String(url).includes("/a/") ? first.promise : second.promise);
    const a = state.useProfile("a");
    const b = state.useProfile("b");
    second.resolve(answer("b"));
    await b;
    first.resolve(answer("a"));
    await a;

    expect(state.profileId()).toBe("b");
    expect(state.watchlist()).toEqual(["b"]);
    expect(state.progressOf("a")).toBeNull();
  });

  test("returning to a profile does not admit its earlier selection response", async () => {
    const first = Promise.withResolvers<Response>();
    const middle = Promise.withResolvers<Response>();
    const latest = Promise.withResolvers<Response>();
    const requests = [first, middle, latest];
    serve(() => {
      const request = requests.shift();
      if (!request) throw new Error("Unexpected state request");
      return request.promise;
    });
    const oldA = state.useProfile("a");
    const b = state.useProfile("b");
    const newA = state.useProfile("a");
    latest.resolve(answer("new-a"));
    await newA;
    first.resolve(answer("old-a"));
    await oldA;
    middle.resolve(answer("b"));
    await b;

    expect(state.profileId()).toBe("a");
    expect(state.watchlist()).toEqual(["new-a"]);
    expect(state.progressOf("old-a")).toBeNull();
  });

  test("clearing selection discards an in-flight initialization", async () => {
    const pending = Promise.withResolvers<Response>();
    serve(() => pending.promise);
    const loading = state.useProfile("a");
    await state.useProfile(null);
    pending.resolve(answer("a"));
    await loading;

    expect(state.profileId()).toBeNull();
    expect(state.watchlist()).toEqual([]);
  });

  test("returning to a profile discards a refresh from its earlier selection", async () => {
    const pending = Promise.withResolvers<Response>();
    serve(() => pending.promise);
    const refreshing = state.refreshState();
    serve(async (url) => answer(String(url).includes("/base/") ? "new-base" : "b"));
    await state.useProfile("b");
    await state.useProfile("base");
    pending.resolve(answer("old-base"));

    expect(await refreshing).toBe(false);
    expect(state.watchlist()).toEqual(["new-base"]);
    expect(state.progressOf("old-base")).toBeNull();
  });
});

const creations = [
  ["profile", state.createProfile, state.profiles, "/api/profiles"],
  ["collection", state.createCollection, state.collections, "/api/profiles/base/collections"],
] as const;

for (const returnToFirst of [false, true]) {
  test(`collection creation is discarded after ${returnToFirst ? "A to B to A" : "A to B"}`, async () => {
    const pending = Promise.withResolvers<Response>();
    const writes: string[] = [];
    serve((url, init) => {
      if (init?.method === "POST") {
        writes.push(String(url));
        return pending.promise;
      }
      return Promise.resolve(answer("selected"));
    });
    const creating = state.createCollection("Old selection");
    await state.useProfile("b");
    if (returnToFirst) await state.useProfile("base");
    pending.resolve(Response.json({ id: "late", name: "Old selection", items: [] }));

    expect(await creating).toBeNull();
    expect(state.collections()).toEqual([]);
    expect(writes).toEqual(["/api/profiles/base/collections"]);
  });
}

const failures = [
  ["HTTP rejection", async () => new Response(null, { status: 503 })],
  ["network rejection", async () => { throw new Error("Connection lost"); }],
  ["invalid JSON", async () => new Response("{", { status: 201 })],
  ["interrupted response body", async () => new Response(new ReadableStream({
    start(controller) { controller.error(new Error("Body interrupted")); },
  }), { status: 201 })],
] as const;

describe.each(creations)("creating a %s", (_kind, create, records, path) => {
  test.each(failures)("returns null without local changes on %s", async (_failure, response) => {
    const before = [...records()];
    serve(response);

    expect(await create("New")).toBeNull();
    expect(records()).toEqual(before);
  });

  test("adds the successfully decoded record once", async () => {
    const made = { id: "new", name: "New", items: [] };
    const before = [...records()];
    const writes: Array<{ url: string; method?: string; body?: RequestInit["body"] }> = [];
    serve(async (url, init) => {
      writes.push({ url: String(url), method: init?.method, body: init?.body });
      return Response.json(made, { status: 201 });
    });

    expect(await create("New")).toEqual(made);
    expect(records()).toEqual([...before, made]);
    expect(writes).toEqual([{ url: path, method: "POST", body: JSON.stringify({ name: "New" }) }]);
  });
});
