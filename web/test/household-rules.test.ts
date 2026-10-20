/**
 * The household rule and its words, held to `household-rules.json`. The
 * Android core and the app run the same file against their ports, so a
 * device decides which household it is in, and who may change one, the same
 * way on the television as in the browser.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import {
  deviceHousehold,
  householdAllowed,
  householdView,
  type Household,
  type HouseholdAction,
  type HouseholdProfile,
  type HouseholdRoleView,
  type HouseholdView,
} from "../public/lib/household-rules.js";
import { HOUSEHOLD_WORDS, householdLine, joinPinTitle } from "../public/lib/household-words.js";

interface Fixture {
  words: typeof HOUSEHOLD_WORDS;
  lines: (
    | { name: string; fn: "householdLine"; args: Parameters<typeof householdLine>; expect: string }
    | { name: string; fn: "joinPinTitle"; args: Parameters<typeof joinPinTitle>; expect: string }
  )[];
  view: { name: string; profiles: HouseholdProfile[]; households: Household[]; expect: HouseholdView }[];
  allowed: {
    name: string;
    profiles: HouseholdRoleView[];
    live: string[];
    actorId: string;
    action: HouseholdAction;
    householdId: string;
    targetId: string;
    expect: boolean;
  }[];
  device: { name: string; stored: string | null; chosen: string | null; live: string[]; heard: boolean; expect: string | null }[];
}

const fixture: Fixture = JSON.parse(
  readFileSync(join(import.meta.dir, "fixtures", "watch-state", "household-rules.json"), "utf8"),
);

describe("household words fixtures", () => {
  test("the words are the fixture's", () => expect(HOUSEHOLD_WORDS).toEqual(fixture.words));

  for (const one of fixture.lines) {
    test(one.name, () => {
      expect(one.fn === "householdLine" ? householdLine(...one.args) : joinPinTitle(...one.args)).toBe(one.expect);
    });
  }
});

describe("household view fixtures", () => {
  for (const one of fixture.view) {
    test(one.name, () => expect(householdView(one.profiles, one.households)).toEqual(one.expect));
  }
});

describe("household allowed fixtures", () => {
  for (const one of fixture.allowed) {
    test(one.name, () => {
      expect(householdAllowed(one.profiles, one.live, one.actorId, one.action, one.householdId, one.targetId)).toBe(
        one.expect,
      );
    });
  }
});

describe("device household fixtures", () => {
  for (const one of fixture.device) {
    test(one.name, () => expect(deviceHousehold(one.stored, one.chosen, one.live, one.heard)).toBe(one.expect));
  }
});
