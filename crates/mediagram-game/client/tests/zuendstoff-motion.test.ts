// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { ZuendstoffScene } from "../src/protocol/ZuendstoffScene";
import type { ZuendstoffStage } from "../src/protocol/ZuendstoffStage";
import {
  CLIP,
  FADE_S,
  FALL_MS,
  PICKUP_MS,
  PICK_DELAY_MS,
  THROW_LEAD_MS,
  frameAt,
  zuendstoffCues,
  type Flight,
} from "../src/tv/zuendstoff/zuendstoff-motion";

// players 10, 11, 12 on seats 0, 1, 2; 10 throws to 12 at 1000, landing at 1000 + 600 + 1000
const THROWN = 1000, LANDS = THROWN + THROW_LEAD_MS + 1000, RELEASE = THROWN + THROW_LEAD_MS;
const sceneOf = (stage: ZuendstoffStage, lives = [3, 3, 3]): ZuendstoffScene => ({
  round: 1,
  seats: lives.map((l, i) => ({ player: 10 + i, lives: l })),
  stage,
});
const flight = (caught: boolean | null): Flight => ({ stage: "flight", from: 10, to: 12, thrown_at: THROWN, lands_at: LANDS, caught });
const at = (s: ZuendstoffStage, now: number, last: Flight | null = null) => frameAt(sceneOf(s), last, 0, now);

test("the holder idles with the bomb in hand, everyone else idles", () => {
  const f = at({ stage: "hold", holder: 11, ready_at: 5000, dropped: false }, 3000);
  expect(f.poses.map((p) => p.clip)).toEqual([CLIP.idle, CLIP.idle, CLIP.idle]);
  expect(f.poses[1].at).toBeNull();
  expect(f.bomb).toEqual({ kind: "hand", seat: 1 });
});

test("the throw starts at the tap so its release falls at the lead-in's end, turning towards the target", () => {
  const start = at(flight(null), THROWN).poses[0];
  expect(start.clip).toBe(CLIP.throw);
  expect(start.at).toBeCloseTo(3.3 - THROW_LEAD_MS / 1000);
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
  const hold: ZuendstoffStage = { stage: "hold", holder: 12, ready_at: LANDS + PICKUP_MS, dropped: true };
  // no memory of the throw needed: the drop flag says when it landed
  expect(at(hold, LANDS + 400).poses[2].clip).toBe(CLIP.miss);
  const bending = at(hold, LANDS + 1000).poses[2];
  expect(bending.clip).toBe(CLIP.pickup);
  expect(bending.rate).toBeGreaterThan(1);
  expect(at(hold, LANDS + PICK_DELAY_MS - 1).bomb).toMatchObject({ kind: "loose", seat: 2 });
  expect(at(hold, LANDS + PICK_DELAY_MS).bomb).toEqual({ kind: "hand", seat: 2 });
  expect(at(hold, LANDS + PICKUP_MS - 10).poses[2].clip).toBe(CLIP.pickup);
  // the host clears the flag at ready_at; the last throw still says it was a drop
  const after: ZuendstoffStage = { ...hold, dropped: false };
  expect(at(after, LANDS + PICKUP_MS + 10, flight(null)).poses[2].clip).toBe(CLIP.idle);
  expect(at(after, LANDS + PICKUP_MS + 10, flight(null)).bomb).toEqual({ kind: "hand", seat: 2 });
});

test("after a catch the catcher finishes the leap and the thrower its follow-through, then both idle facing the middle", () => {
  const hold: ZuendstoffStage = { stage: "hold", holder: 12, ready_at: LANDS, dropped: false };
  const f = at(hold, LANDS + 500, flight(true));
  expect(f.poses[2].clip).toBe(CLIP.catch);
  expect(f.poses[2].at).toBeCloseTo(1.35);
  // the throw clip ends bent over as the bomb lands
  const end = RELEASE + 1000;
  expect(at(flight(true), end - 1).poses[0].clip).toBe(CLIP.throw);
  const turning = at(hold, end + (FADE_S * 1000) / 2, flight(true)).poses[0];
  expect(turning.clip).toBe(CLIP.idle);
  expect(turning.aim?.k).toBeCloseTo(0.5);
  expect(at(hold, end + FADE_S * 1000 + 1, flight(true)).poses[0].aim).toBeNull();
  expect(at(hold, LANDS + 3000, flight(true)).poses.map((p) => p.clip)).toEqual([CLIP.idle, CLIP.idle, CLIP.idle]);
  // without the throw in memory (a TV joining now) the holder just idles
  expect(at(hold, LANDS + 500).poses[2].clip).toBe(CLIP.idle);
});

test("a new round's holder is not mistaken for the last throw's catcher", () => {
  const hold: ZuendstoffStage = { stage: "hold", holder: 12, ready_at: LANDS + 9000, dropped: false };
  expect(at(hold, LANDS + 7000, flight(null)).poses[2].clip).toBe(CLIP.idle);
});

test("the blast fells its victim from when the TV saw it; the winner cheers", () => {
  const boom = frameAt(sceneOf({ stage: "boom", victim: 11, out: false }), null, 5000, 5800);
  expect(boom.poses[1]).toMatchObject({ clip: CLIP.fall, at: 0.8 });
  expect(boom.bomb).toEqual({ kind: "blast", seat: 1, ms: 800 });
  const final = at({ stage: "final", order: [12, 10, 11] }, 9000);
  expect(final.poses.map((p) => p.clip)).toEqual([CLIP.idle, CLIP.idle, CLIP.win]);
  expect(final.bomb).toEqual({ kind: "none" });
});

test("a landing makes one sound, whichever stage says first how it ended", () => {
  const caught = zuendstoffCues(sceneOf(flight(true)), 0, LANDS - 100);
  expect(caught).toEqual({ key: `land ${LANDS}`, cues: [{ sound: "knock", at: LANDS }] });
  expect(zuendstoffCues(sceneOf(flight(null)), 0, LANDS)).toBeNull();
  const fumble = zuendstoffCues(sceneOf(flight(false)), 0, LANDS);
  const drop = zuendstoffCues(sceneOf({ stage: "hold", holder: 12, ready_at: LANDS + PICKUP_MS, dropped: true }), 0, LANDS + 400);
  expect(fumble).toEqual(drop);
  expect(drop?.cues).toEqual([{ sound: "thunk", at: LANDS + FALL_MS }]);
});
