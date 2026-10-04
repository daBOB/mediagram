/**
 * Runs `pin-hash.json`, `profile-rules.json`, `profile-names.json` and
 * `pin-wait.json` against the web's PIN hash, its rule for who may manage
 * whom, the names it refuses and the wait after wrong PINs. The Android core
 * runs the same files against its port, so these are what hold the two
 * surfaces to one answer: a PIN set on the television opens the profile on
 * the laptop, and what a parent may do on one it may do on the other.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { hashPin, pinMatches } from "../src/state/profiles-pin";
import { allowed, nameTaken, type Action, type RoleView } from "../src/state/profiles-rules";
import { PinWait } from "../src/state/profiles-wait";

const FIXTURES = join(import.meta.dir, "fixtures", "watch-state");

function load<T>(file: string): T {
  return JSON.parse(readFileSync(join(FIXTURES, file), "utf8")) as T;
}

describe("pin-hash fixtures", () => {
  for (const one of load<{ salt: string; pin: string; hash: string }[]>("pin-hash.json")) {
    test(`salt ${one.salt.slice(0, 8)}… with PIN ${one.pin}`, () => {
      expect(hashPin(one.salt, one.pin)).toBe(one.hash);
      expect(pinMatches(one.hash, one.salt, one.pin)).toBe(true);
    });
  }
});

describe("profile-rules fixtures", () => {
  interface Case {
    name: string;
    profiles: RoleView[];
    actorId: string;
    action: Action;
    targetId: string;
    expect: boolean;
  }

  for (const one of load<Case[]>("profile-rules.json")) {
    test(one.name, () => {
      expect(allowed(one.profiles, one.actorId, one.action, one.targetId)).toBe(one.expect);
    });
  }
});

describe("profile-names fixtures", () => {
  for (const one of load<{ name: string; existing: string[]; candidate: string; expect: boolean }[]>("profile-names.json")) {
    test(one.name, () => expect(nameTaken(one.existing, one.candidate)).toBe(one.expect));
  }
});

describe("pin-wait fixtures", () => {
  /** One step on the wait's clock, `at` milliseconds after the case began. */
  type Step = { at: number; failed?: string; succeeded?: string; secondsLeft?: string; expect?: number };

  for (const one of load<{ name: string; steps: Step[] }[]>("pin-wait.json")) {
    test(one.name, () => {
      let now = 1_000_000;
      const wait = new PinWait(() => now);
      for (const step of one.steps) {
        now = 1_000_000 + step.at;
        if (step.failed !== undefined) wait.failed(step.failed);
        else if (step.succeeded !== undefined) wait.succeeded(step.succeeded);
        else expect(wait.secondsLeft(step.secondsLeft!)).toBe(step.expect!);
      }
    });
  }
});
