/** The daily limit's rule, words and choices, held to the fixture the Android app is held to as well. */

import { afterAll, expect, mock, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { DAILY_LIMIT_CHOICES, DAILY_LIMIT_WORDS, WARN_SECONDS, dailyLimitState } from "../public/lib/daily-limit.js";

interface Fixture {
  words: typeof DAILY_LIMIT_WORDS;
  choices: typeof DAILY_LIMIT_CHOICES;
  warnSeconds: number;
  cases: { name: string; limitMinutes: number | null; watchedSeconds: number; expect: ReturnType<typeof dailyLimitState> }[];
}

const fixture: Fixture = JSON.parse(readFileSync(join(import.meta.dir, "fixtures", "watch-state", "daily-limit.json"), "utf8"));

test("the words, the choices and the warning threshold are the fixture's", () => {
  expect(DAILY_LIMIT_WORDS).toEqual(fixture.words);
  expect(DAILY_LIMIT_CHOICES).toEqual(fixture.choices);
  expect(WARN_SECONDS).toBe(fixture.warnSeconds);
});

for (const one of fixture.cases) {
  test(one.name, () => {
    expect(dailyLimitState(one.limitMinutes, one.watchedSeconds)).toEqual(one.expect);
  });
}

// The gate asks the page's watch state and the server; both are stood in for,
// and the real state module is put back so no other test sees the stub.
const statePath = join(import.meta.dir, "..", "public", "lib", "watch-state.js");
const realState = await import(statePath);
mock.module(statePath, () => ({ ...realState, profileId: () => "p1", profile: () => ({ kids: true }) }));
afterAll(() => mock.module(statePath, () => realState));

test("a book opens at the limit", async () => {
  const realFetch = globalThis.fetch;
  const realDocument = (globalThis as any).document;
  globalThis.fetch = (async () => Response.json({ limitMinutes: 10, watchedSeconds: 600 })) as unknown as typeof fetch;
  (globalThis as any).document = { getElementById: () => null, createElement: () => ({ setAttribute() {}, remove() {}, append() {}, classList: { add() {} }, set textContent(_: string) {} }) };
  try {
    const { mayOpen } = await import("../public/lib/daily-limit-gate.js");
    expect(await mayOpen({ kind: "book" })).toBe(true);
    expect(await mayOpen({ kind: "movie" })).toBe(false);
  } finally {
    globalThis.fetch = realFetch;
    (globalThis as any).document = realDocument;
  }
});
