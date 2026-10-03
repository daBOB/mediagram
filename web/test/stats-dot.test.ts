/** The new-achievement dot on the rail's Stats link: when it lights, and when it goes out. */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { markSeen, SETTLE_MS, watchStatsDot } from "../public/lib/catalog/stats-dot.js";
import { browserEnvironment, settle } from "./support/player-environment";

const DOT = "stats-dot";

let env: ReturnType<typeof browserEnvironment>;
let stored: Map<string, string>;
let earnedBy: Record<string, string[]>;
let chosen: string | null;
let notify: () => void;

const state = {
  profileId: () => chosen,
  subscribeChanges: (listener: () => void) => { notify = listener; },
};
const dot = () => env.node(DOT);
const statsReads = () => env.requests.filter((request) => request.url.endsWith("/stats")).length;

beforeEach(() => {
  env = browserEnvironment();
  stored = new Map();
  Object.assign(env.window, {
    localStorage: {
      getItem: (key: string) => stored.get(key) ?? null,
      setItem: (key: string, value: string) => void stored.set(key, value),
      removeItem: (key: string) => void stored.delete(key),
    },
  });
  earnedBy = { anna: [], ben: [] };
  chosen = "anna";
  env.respondWith(async (url) => {
    const profile = decodeURIComponent(url.split("/")[3]!);
    const earned = (earnedBy[profile] ?? []).map((id) => ({ id, earnedAt: 1 }));
    return Response.json({ achievements: { earned, next: [] } });
  });
  dot().hidden = true;
});
afterEach(() => env.restore());

describe("the Stats dot", () => {
  test("lights for an achievement this browser has not shown", async () => {
    earnedBy.anna = ["films-1"];
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(false);
  });

  test("stays out when everything earned has been shown", async () => {
    earnedBy.anna = ["films-1"];
    stored.set("mediagram.stats-seen.anna", JSON.stringify(["films-1"]));
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(true);
  });

  test("lights once a sync brings an achievement earned on another device", async () => {
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(true);

    earnedBy.anna = ["streak-7"];
    notify(); // refreshState pulled another device's rows
    await settle();
    expect(dot().hidden).toBe(true); // not yet: changes settle first
    env.advance(SETTLE_MS);
    await settle();
    expect(dot().hidden).toBe(false);
  });

  test("opening the Stats page marks what it shows and puts the dot out", async () => {
    earnedBy.anna = ["films-1", "genres-5"];
    watchStatsDot(state);
    await settle();
    markSeen("anna", [{ id: "films-1", earnedAt: 1 }, { id: "genres-5", earnedAt: 2 }]);
    expect(dot().hidden).toBe(true);
    expect(JSON.parse(stored.get("mediagram.stats-seen.anna")!)).toEqual(["films-1", "genres-5"]);

    notify();
    env.advance(SETTLE_MS);
    await settle();
    expect(dot().hidden).toBe(true);
  });

  test("seen is kept per profile, and a switch reads the new profile at once", async () => {
    earnedBy = { anna: ["films-1"], ben: ["films-1"] };
    stored.set("mediagram.stats-seen.anna", JSON.stringify(["films-1"]));
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(true);

    chosen = "ben";
    notify();
    await settle();
    expect(dot().hidden).toBe(false);
  });

  test("a switch puts the last profile's dot out before the new answer arrives", async () => {
    earnedBy.anna = ["films-1"];
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(false);

    env.respondWith(() => new Promise<Response>(() => {}));
    chosen = "ben";
    notify();
    expect(dot().hidden).toBe(true);
  });

  test("an answer for a profile no longer chosen is dropped", async () => {
    earnedBy.anna = ["films-1"];
    let release!: () => void;
    const held = new Promise<void>((resolve) => { release = resolve; });
    env.respondWith(async () => {
      await held;
      return Response.json({ achievements: { earned: [{ id: "films-1", earnedAt: 1 }], next: [] } });
    });
    watchStatsDot(state);
    chosen = null;
    release();
    await settle();
    expect(dot().hidden).toBe(true);
  });

  test("an unreadable seen entry counts as nothing shown", async () => {
    earnedBy.anna = ["films-1"];
    stored.set("mediagram.stats-seen.anna", "{not json");
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(false);
  });

  test("positions saved while a title plays do not re-read the stats", async () => {
    watchStatsDot(state);
    await settle();
    const before = statsReads();
    for (let tick = 0; tick < 6; tick++) {
      notify();
      env.advance(10_000);
    }
    await settle();
    expect(statsReads()).toBe(before);
    env.advance(SETTLE_MS);
    await settle();
    expect(statsReads()).toBe(before + 1);
  });
});
