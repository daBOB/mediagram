// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { ZuendstoffScene } from "../src/protocol/ZuendstoffScene";
import type { ZuendstoffStage } from "../src/protocol/ZuendstoffStage";
import {
  CLIP,
  FADE_S,
  FALL_MS,
  frameAt,
  pickDelayMs,
  zuendstoffCues,
  type Flight,
} from "../src/tv/zuendstoff/zuendstoff-motion";

// players 10, 11, 12 on seats 0, 1, 2; 10 throws to 12 at 1000, lets go 600 later and it lands 1000 after that; a drop
// is picked up 2000 after the landing
const LEAD = 600, PICKUP = 2000, PICK_DELAY = pickDelayMs(PICKUP);
const THROWN = 1000, RELEASE = THROWN + LEAD, LANDS = RELEASE + 1000;
const sceneOf = (stage: ZuendstoffStage, lives = [3, 3, 3]): ZuendstoffScene => ({
  round: 1,
  seats: lives.map((l, i) => ({ player: 10 + i, lives: l })),
  stage,
});
const flight = (caught: boolean | null): Flight => ({ stage: "flight", from: 10, to: 12, thrown_at: THROWN, released_at: RELEASE, lands_at: LANDS, caught });
const hold = (holder: number, since: number, ready_at: number, caught: boolean | null): ZuendstoffStage => ({ stage: "hold", holder, since, ready_at, caught });
const at = (s: ZuendstoffStage, now: number, last: Flight | null = null) => frameAt(sceneOf(s), last, now);

test("the holder idles with the bomb in hand, everyone else idles", () => {
  const f = at(hold(11, 2500, 5000, null), 3000);
  expect(f.poses.map((p) => p.clip)).toEqual([CLIP.idle, CLIP.idle, CLIP.idle]);
  expect(f.poses[1].at).toBeNull();
  expect(f.bomb).toEqual({ kind: "hand", seat: 1 });
});

test("the throw starts at the tap so its release falls at the lead-in's end, turning towards the target", () => {
  const start = at(flight(null), THROWN).poses[0];
  expect(start.clip).toBe(CLIP.throw);
  expect(start.at).toBeCloseTo(3.3 - LEAD / 1000);
  expect(start.aim).toEqual({ to: 2, k: 0 });
  const release = at(flight(null), RELEASE).poses[0];
  expect(release.at).toBeCloseTo(3.3);
  expect(release.aim).toEqual({ to: 2, k: 1 });
  // the bomb stays in the hand until the release, then flies until the landing
  expect(at(flight(null), RELEASE - 1).bomb).toEqual({ kind: "hand", seat: 0 });
  expect(at(flight(null), RELEASE + 500).bomb).toEqual({ kind: "flight", from: 0, to: 2, k: 0.5 });
});

test("the target leaps so the top of the leap meets the landing; a fumble jumps for it instead", () => {
  expect(at(flight(null), THROWN).poses[2].clip).toBe(CLIP.idle);
  const leap = at(flight(null), LANDS).poses[2];
  expect(leap.clip).toBe(CLIP.catch);
  expect(leap.at).toBeCloseTo(0.85);
  const miss = at(flight(false), LANDS).poses[2];
  expect(miss.clip).toBe(CLIP.miss);
  expect(miss.at).toBeCloseTo(1.55);
});

test("a caught bomb lands in the target's hand; an unresolved or fumbled one falls", () => {
  expect(at(flight(true), LANDS).bomb).toEqual({ kind: "hand", seat: 2 });
  expect(at(flight(null), LANDS + 200).bomb).toEqual({ kind: "loose", seat: 2, ms: 200 });
  expect(at(flight(false), LANDS + 100).bomb).toEqual({ kind: "loose", seat: 2, ms: 100 });
});

test("after a drop the holder falls, then picks the bomb up and stands by the time they may throw", () => {
  const dropped = hold(12, LANDS, LANDS + PICKUP, false);
  // no memory of the throw needed: the hold says when it landed and that it fell
  expect(at(dropped, LANDS + 400).poses[2].clip).toBe(CLIP.miss);
  const bending = at(dropped, LANDS + 1000).poses[2];
  expect(bending.clip).toBe(CLIP.pickup);
  expect(bending.rate).toBeGreaterThan(1);
  expect(at(dropped, LANDS + PICK_DELAY - 1).bomb).toMatchObject({ kind: "loose", seat: 2 });
  expect(at(dropped, LANDS + PICK_DELAY).bomb).toEqual({ kind: "hand", seat: 2 });
  expect(at(dropped, LANDS + PICKUP - 10).poses[2].clip).toBe(CLIP.pickup);
  expect(at(dropped, LANDS + PICKUP + 10, flight(null)).poses[2].clip).toBe(CLIP.idle);
  expect(at(dropped, LANDS + PICKUP + 10, flight(null)).bomb).toEqual({ kind: "hand", seat: 2 });
});

test("after a catch the catcher finishes the leap and the thrower its follow-through, then both idle facing the middle", () => {
  // the host makes a catcher wait 600 ms after the tick that saw the catch, so a second tap throws nothing
  const caught = hold(12, LANDS, LANDS + 650, true);
  const f = at(caught, LANDS + 500, flight(true));
  expect(f.poses[2].clip).toBe(CLIP.catch);
  expect(f.poses[2].at).toBeCloseTo(1.35);
  // the throw clip ends bent over as the bomb lands
  const end = RELEASE + 1000;
  expect(at(flight(true), end - 1).poses[0].clip).toBe(CLIP.throw);
  const turning = at(caught, end + (FADE_S * 1000) / 2, flight(true)).poses[0];
  expect(turning.clip).toBe(CLIP.idle);
  expect(turning.aim?.k).toBeCloseTo(0.5);
  expect(at(caught, end + FADE_S * 1000 + 1, flight(true)).poses[0].aim).toBeNull();
  expect(at(caught, LANDS + 3000, flight(true)).poses.map((p) => p.clip)).toEqual([CLIP.idle, CLIP.idle, CLIP.idle]);
  // a TV joining now, without the throw in memory, still shows the catch
  expect(at(caught, LANDS + 500).poses[2].clip).toBe(CLIP.catch);
});

test("a new round's holder is not mistaken for the last throw's catcher", () => {
  expect(at(hold(12, LANDS + 6500, LANDS + 9000, null), LANDS + 7000, flight(null)).poses[2].clip).toBe(CLIP.idle);
});

test("the blast fells its victim from when it went off; the winner cheers", () => {
  const boom = at({ stage: "boom", victim: 11, out: false, at: 5000 }, 5800);
  expect(boom.poses[1]).toMatchObject({ clip: CLIP.fall, at: 0.8 });
  expect(boom.bomb).toEqual({ kind: "blast", seat: 1, ms: 800 });
  const final = at({ stage: "final", order: [12, 10, 11] }, 9000);
  expect(final.poses.map((p) => p.clip)).toEqual([CLIP.idle, CLIP.idle, CLIP.win]);
  expect(final.bomb).toEqual({ kind: "none" });
});

test("a landing makes one sound, whichever stage says first how it ended", () => {
  const caught = zuendstoffCues(sceneOf(flight(true)), LANDS - 100);
  expect(caught).toEqual({ key: `land ${LANDS}`, cues: [{ sound: "knock", at: LANDS }] });
  expect(zuendstoffCues(sceneOf(flight(null)), LANDS)).toBeNull();
  const fumble = zuendstoffCues(sceneOf(flight(false)), LANDS);
  const drop = zuendstoffCues(sceneOf(hold(12, LANDS, LANDS + PICKUP, false)), LANDS + 400);
  expect(fumble).toEqual(drop);
  expect(drop?.cues).toEqual([{ sound: "thunk", at: LANDS + FALL_MS }]);
});
