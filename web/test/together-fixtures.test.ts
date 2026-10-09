/**
 * Runs the JSON fixtures under `fixtures/together/` against the watch-together
 * session rules. The Kotlin port in `android/core/model` reads the same files.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { acted, finished, joined, left, started } from "../public/lib/together/moves.js";
import {
  changeOf, decide, expectedPosition, follow, isLive,
  type Change, type ScreenState, type Step,
} from "../public/lib/together/follow.js";
import { parseSession, writeSession, type Session } from "../public/lib/together/session.js";

const FIXTURES = join(import.meta.dir, "fixtures", "together");

function load<T>(file: string): T {
  return JSON.parse(readFileSync(join(FIXTURES, file), "utf8")) as T;
}

describe("format fixtures", () => {
  for (const one of load<{ name: string; text: string; expect: Session | null }[]>("format.json")) {
    test(one.name, () => {
      const parsed = parseSession(one.text);
      expect(parsed).toEqual(one.expect);
      if (parsed !== null) expect(parseSession(writeSession(parsed))).toEqual(parsed);
    });
  }
});

describe("move fixtures", () => {
  interface Case {
    name: string;
    move: "started" | "joined" | "left" | "acted" | "finished";
    session?: Session;
    args: any;
    expect: Session;
  }
  for (const one of load<Case[]>("moves.json")) {
    test(one.name, () => {
      const { args } = one;
      const s = one.session as Session;
      const got =
        one.move === "started" ? started(args.id, args.me, args.own, args.now)
        : one.move === "joined" ? joined(s, args.me)
        : one.move === "left" ? left(s, args.screen)
        : one.move === "finished" ? finished(s, args.screen)
        : acted(s, args.change, args.me, args.now);
      expect(got).toEqual(one.expect);
    });
  }
});

const TOLERANCE = 1e-6;

describe("expected position fixtures", () => {
  for (const one of load<{ name: string; session: Session; now: number; expect: number }[]>("expected.json")) {
    test(one.name, () => {
      expect(Math.abs(expectedPosition(one.session, one.now) - one.expect)).toBeLessThan(TOLERANCE);
    });
  }
});

describe("liveness fixtures", () => {
  for (const one of load<{ name: string; session: Session; duration?: number | null; now: number; expect: boolean }[]>("liveness.json")) {
    test(one.name, () => {
      expect(isLive(one.session, one.duration as number | null, one.now)).toBe(one.expect);
    });
  }
});

describe("follow fixtures", () => {
  interface Case { name: string; current: Session | null; incoming: Session; me: string; expect: "current" | "incoming" }
  for (const one of load<Case[]>("follow.json")) {
    test(one.name, () => {
      expect(follow(one.current, one.incoming, one.me)).toBe(one.expect === "current" ? (one.current as Session) : one.incoming);
    });
  }
});

describe("decide fixtures", () => {
  for (const one of load<{ name: string; own: ScreenState; session: Session; now: number; expect: Step[] }[]>("decide.json")) {
    test(one.name, () => {
      const got = decide(one.own, one.session, one.now);
      expect(got.length).toBe(one.expect.length);
      one.expect.forEach((want, i) => {
        const step = got[i] as Step;
        if ("pos" in want && "pos" in step) {
          expect(Math.abs(step.pos - want.pos)).toBeLessThan(TOLERANCE);
          expect({ ...step, pos: 0 }).toEqual({ ...want, pos: 0 });
        } else {
          expect(step).toEqual(want);
        }
      });
    });
  }
});

describe("change fixtures", () => {
  for (const one of load<{ name: string; prev: Session; next: Session; expect: { change: Change; label: string | null } | null }[]>("change.json")) {
    test(one.name, () => {
      expect(changeOf(one.prev, one.next)).toEqual(one.expect);
    });
  }
});
