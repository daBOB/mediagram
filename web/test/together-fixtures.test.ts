/**
 * Runs the JSON fixtures under `fixtures/together/` against the watch-together
 * session rules. The Kotlin port in `android/core/model` reads the same files.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { acted, finished, joined, left, started } from "../public/lib/together/moves.js";
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
