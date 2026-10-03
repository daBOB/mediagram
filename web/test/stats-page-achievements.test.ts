/** The Stats page with its Achievements section in place, and what drawing it records. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderStats } from "../public/lib/catalog/stats-page.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, settle } from "./support/player-environment";
import { textOf } from "./support/browser-application";

let env: ReturnType<typeof browserEnvironment>;
let stored: Map<string, string>;
beforeEach(async () => {
  env = browserEnvironment();
  stored = new Map();
  Object.assign(env.window, {
    localStorage: {
      getItem: (key: string) => stored.get(key) ?? null,
      setItem: (key: string, value: string) => void stored.set(key, value),
      removeItem: (key: string) => void stored.delete(key),
    },
  });
  env.respondWith(async () => Response.json({}));
  await state.useProfile("viewer");
});
afterEach(async () => {
  await settle();
  await state.useProfile(null);
  env.restore();
});

const SUMMARY = {
  weekSeconds: 600,
  monthSeconds: 600,
  allSeconds: 600,
  last30: Array.from({ length: 30 }, (_, i) => ({ day: `2026-09-${String(i + 1).padStart(2, "0")}`, seconds: i === 29 ? 600 : 0 })),
  history: [{ kind: "finished", setId: "01FILM", at: 0, seconds: 600 }],
  achievements: { earned: [{ id: "films-1", earnedAt: 0 }], next: [{ id: "films-10", have: 1, need: 10 }] },
};

test("Achievements sit between the last thirty days and the history", async () => {
  env.respondWith(async () => Response.json(SUMMARY));
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => true);
  expect(main.children.map((node) => node.className)).toEqual([
    "shelf-head", "stats-totals", "stats-days", "stats-achievements", "stats-history",
  ]);
  expect(textOf(main.children[3]!)).toContain("First film");
  expect(textOf(main.children[3]!)).toContain("1 of 10 films");
});

test("nothing earned and nothing to come draws no section", async () => {
  env.respondWith(async () => Response.json({ ...SUMMARY, achievements: { earned: [], next: [] } }));
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => true);
  expect(main.children.map((node) => node.className)).not.toContain("stats-achievements");
});
