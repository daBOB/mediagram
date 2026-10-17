/** The daily limit's rule, words and choices, held to the fixture the Android app is held to as well. */

import { expect, test } from "bun:test";
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
