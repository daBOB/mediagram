/** The Stats page, drawn from a stubbed `/stats` answer. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderStats } from "../public/lib/catalog/stats-page.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, settle } from "./support/player-environment";
import { descendants, textOf } from "./support/browser-application";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(async () => {
  env = browserEnvironment();
  env.respondWith(async () => Response.json({}));
  await state.useProfile("viewer");
});
afterEach(async () => {
  await settle();
  await state.useProfile(null);
  env.restore();
});

const days = (seconds: (day: number) => number) =>
  Array.from({ length: 30 }, (_, i) => ({ day: `2026-09-${String(i + 1).padStart(2, "0")}`, seconds: seconds(i) }));

const SUMMARY = {
  weekSeconds: 42 * 60, monthSeconds: 3 * 3600 + 12 * 60, allSeconds: 10 * 3600,
  last30: days((i) => (i === 29 ? 600 : i === 0 ? 300 : 0)),
  history: [
    { kind: "finished", setId: "01A", at: 0, seconds: 7200 },
    { kind: "started", setId: "01GONE", at: 0, seconds: 120 },
  ],
};

function answer(body: unknown, status = 200) {
  env.respondWith(async (url) =>
    url.endsWith("/stats") ? Response.json(body, { status }) : Response.json({}));
}

test("asks for this profile's own stats and draws totals, thirty bars and the history", async () => {
  answer(SUMMARY);
  const main = env.node("main");
  const byId = new Map([["01A", { setId: "01A", kind: "movie", title: "Der Pate", show: null }]]);
  await renderStats(main, { byId }, () => true);

  expect(env.requests.at(-1)!.url).toBe("/api/profiles/viewer/stats");
  const text = textOf(main);
  expect(text).toContain("This week42 min");
  expect(text).toContain("This month3 h 12 min");
  expect(text).toContain("All time10 h");
  const bars = descendants(main).filter((node) => node.className === "stats-bar");
  expect(bars).toHaveLength(30);
  expect(bars.at(-1)!.style.height).toBe("100%");
  expect(bars[0]!.style.height).toBe("50%");
  expect(bars[1]!.style.height).toBe("0%");
  // The page's clock is the test environment's, which stands at 0.
  const lines = descendants(main).filter((node) => node.tagName === "LI").map(textOf);
  expect(lines).toHaveLength(2);
  expect(lines[0]).toMatch(/^Finished · Der Pate · today \d\d:\d\d · 2 h$/);
  expect(lines[1]).toMatch(/^Started · No longer in the library · today \d\d:\d\d · 2 min$/);
});

test("nothing watched yet says so under the heading, and nothing else", async () => {
  answer({ ...SUMMARY, weekSeconds: 0, monthSeconds: 0, allSeconds: 0, history: [] });
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => true);
  expect(textOf(main)).toBe("StatsNothing watched yet.");
});

test("an answer that lands after the viewer left draws nothing", async () => {
  answer(SUMMARY);
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => false);
  expect(textOf(main)).toBe("Stats");
});

test("a failed read says so", async () => {
  answer({}, 500);
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => true);
  expect(textOf(main)).toContain("Could not read your stats: the server answered 500");
});
