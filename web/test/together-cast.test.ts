/**
 * Runs `fixtures/together/cast.json` against the cast rules and words. The
 * Kotlin port in `android/core/model` reads the same file.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import {
  accepted, answerOf, busied, chose, endingOf, offered, refusedBy, replaced, tracked,
  type TvAnswer,
} from "../public/lib/together/cast.js";
import {
  ageReason, barLine, busyQuestion, CAST_WORDS, castFrom, refusedLine, sayOnRemote, timeReason,
} from "../public/lib/together/cast-words.js";
import { parseSession, writeSession, type Session } from "../public/lib/together/session.js";

const FIXTURES = join(import.meta.dir, "fixtures", "together");
const cast = JSON.parse(readFileSync(join(FIXTURES, "cast.json"), "utf8"));

const roundTrips = (s: Session | null) => {
  if (s !== null) expect(parseSession(writeSession(s))).toEqual(s);
};

describe("cast words", () => {
  test("the words are the fixture's", () => expect(CAST_WORDS).toEqual(cast.words.object));
  const formats: Record<string, (arg: string) => string> = {
    busyQuestion, refusedLine, castFrom, barLine, ageReason, timeReason,
  };
  for (const one of cast.words.lines) {
    test(one.name, () => expect(formats[one.fn]!(one.arg)).toBe(one.expect));
  }
});

describe("offer fixtures", () => {
  for (const one of cast.offered) {
    test(one.name, () => {
      const { id, me, own, profile, household, now } = one.args;
      expect(offered(id, me, own, profile, household, now)).toEqual(one.expect);
    });
  }
});

describe("answer fixtures", () => {
  for (const one of cast.answer) {
    test(one.name, () => expect(answerOf(one.session, one.tv, one.now) as TvAnswer).toEqual(one.expect));
  }
});

describe("cast move fixtures", () => {
  for (const one of cast.moves) {
    test(one.name, () => {
      const { args } = one;
      const s = one.session as Session;
      const got =
        one.move === "busied" ? busied(s, args.tv, args.note)
        : one.move === "refusedBy" ? refusedBy(s, args.tv, args.note)
        : one.move === "replaced" ? replaced(s, args.me, args.own, args.now)
        : one.move === "accepted" ? accepted(s, args.me, args.now)
        : one.move === "tracked" ? tracked(s, args.tv, args.tracks, args.audio, args.text)
        : chose(s, args.screen, args.choice);
      expect(got).toEqual(one.expect);
      roundTrips(got);
    });
  }
});

describe("ending fixtures", () => {
  for (const one of cast.ending) {
    test(one.name, () => expect(endingOf(one.session, one.me)).toBe(one.expect));
  }
});

describe("remote notice fixtures", () => {
  for (const one of cast.remoteNotices) {
    test(one.name, () => expect(sayOnRemote(one.noticed, one.session)).toBe(one.expect));
  }
});

test("a full cast stays under Telegram's 4096 characters", () => {
  const phone = (n: number) => ({ screen: `screen-${n}`, label: "Phone", remote: true });
  const lists = (n: number, p: string) => Array.from({ length: n }, (_, i) => ({ id: `${p}${String(i).padStart(15, "0")}`, label: "x".repeat(80) }));
  let s = offered("s1", phone(0), { set: "film-1", playing: true, pos: 100, rate: 1 }, "p1", "home", 1000);
  s = accepted(s, { screen: "tv-1", label: "TV", remote: false }, 2000)!;
  s = { ...s, members: [...s.members, phone(1), phone(2)] };
  s = tracked(s, "tv-1", { audio: lists(30, "a"), text: lists(40, "t") }, "a000000000000000", "t000000000000000");
  expect(s.members.length).toBe(4);
  expect(writeSession(s).length).toBeLessThan(4096);
  roundTrips(s);
});
