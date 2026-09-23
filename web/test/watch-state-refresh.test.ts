/**
 * A tab left open reads positions again before resuming.
 *
 * The page read its profile's state once, when the profile was chosen, so a
 * viewer who watched further on the phone and came back to an open tab was
 * resumed at the old position. `refreshState` fetches the newer one — without
 * letting the server's older copy undo a position this tab just wrote.
 */

import { beforeEach, describe, expect, test } from "bun:test";

import * as state from "../public/lib/watch-state.js";

const SET = "01SET0000000000000000001";
let served: unknown;

beforeEach(async () => {
  const store = new Map<string, string>();
  (globalThis as any).window = {
    localStorage: {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
      removeItem: (k: string) => void store.delete(k),
    },
  };
  (globalThis as any).fetch = async (url: string, init?: RequestInit) => {
    if (init?.method && init.method !== "GET") return new Response(null, { status: 204 });
    if (String(url).endsWith("/state")) return Response.json(served);
    return new Response(null, { status: 404 });
  };
  served = { progress: [{ setId: SET, at: 600, duration: 3000, updatedAt: 1_000 }] };
  await state.useProfile("p1");
});

describe("refreshState", () => {
  test("takes a newer position the server learned from another device", async () => {
    served = { progress: [{ setId: SET, at: 1800, duration: 3000, updatedAt: 2_000 }] };

    const changed = await state.refreshState();

    expect(changed).toBe(true);
    expect(state.progressOf(SET)?.at).toBe(1800);
  });

  test("keeps a position this tab wrote that the server does not have yet", async () => {
    state.setProgress(SET, 900, 3000);
    // The write has not landed: the server still answers with the old row.

    const changed = await state.refreshState();

    expect(changed).toBe(false);
    expect(state.progressOf(SET)?.at).toBe(900);
  });

  test("a title finished elsewhere loses its position here", async () => {
    served = { progress: [], watched: [{ setId: SET, finishedAt: 2_000 }] };

    await state.refreshState();

    expect(state.progressOf(SET)).toBeNull();
  });

  test("says nothing changed when nothing did", async () => {
    expect(await state.refreshState()).toBe(false);
    expect(state.progressOf(SET)?.at).toBe(600);
  });

  test("an answer for a profile no longer chosen is dropped", async () => {
    served = { progress: [{ setId: SET, at: 1800, duration: 3000, updatedAt: 2_000 }] };
    const pending = state.refreshState();
    served = { progress: [] };
    await state.useProfile("p2");

    expect(await pending).toBe(false);
    expect(state.progressOf(SET)).toBeNull();
  });
});
