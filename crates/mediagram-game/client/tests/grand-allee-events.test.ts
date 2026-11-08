// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { GAP, SceneWatch, changesBetween } from "../src/tv/grand-allee/grand-allee-events";
import { sceneOf } from "./grand-allee-fixture";

const before = sceneOf({ estates: [{ hotel: 3, owner: 1, levels: 0 }, { hotel: 7, owner: 2, levels: 4 }], entrances: [{ field: 13, hotel: 7 }] });

test("the first scene a TV sees changes nothing: a TV that reconnects replays nothing", () => {
  expect(changesBetween(null, before)).toEqual({ owners: [], entrances: [], out: [], cash: new Map() });
});

test("a forced purchase, a purchase, an entrance, a bankruptcy and cash moving are read off the difference", () => {
  const after = structuredClone(before);
  after.estates[0].owner = 0; // taken over by force
  after.estates.push({ hotel: 5, owner: 3, levels: 0 }); // bought
  after.entrances.push({ field: 16, hotel: 7 });
  after.seats[1].out = true;
  after.seats[0].cash -= 1000;
  after.seats[1].cash += 1000;
  expect(changesBetween(before, after)).toEqual({ owners: [3, 5], entrances: [16], out: [1], cash: new Map([[0, -1000], [1, 1000]]) });
});

// a later scene with a plot taken over and cash moved: what a TV that missed the scenes between would otherwise replay
const later = () => {
  const s = structuredClone(before);
  s.estates[0].owner = 0;
  s.seats[0].cash -= 2500;
  return s;
};
const NOTHING = { owners: [], entrances: [], out: [], cash: new Map() };

test("scenes in step report what changed, and nothing while the scene stands", () => {
  const w = new SceneWatch(), next = later();
  expect(w.next(before, 0)).toEqual({ changes: NOTHING, fresh: true });
  expect(w.next(before, 0.016)).toBeNull();
  expect(w.next(next, 0.033)).toEqual({ changes: { ...NOTHING, owners: [3], cash: new Map([[0, -2500]]) }, fresh: false });
});

test("frames that stopped (a paused WebView, a box asleep) start a fresh comparison: nothing is replayed", () => {
  const w = new SceneWatch();
  w.next(before, 10);
  expect(w.next(later(), 10 + GAP.frame + 0.5)).toEqual({ changes: NOTHING, fresh: true });
});

test("a scene after a silence longer than any stage (the socket was down, frames ran on) starts afresh", () => {
  const w = new SceneWatch();
  let t = 0;
  w.next(before, t);
  for (; t < GAP.silence + 5; t += 0.016) expect(w.next(before, t)).toBeNull();
  expect(w.next(later(), t)).toEqual({ changes: NOTHING, fresh: true });
});

test("a stage built or a plot given back to the bank is no change of owner", () => {
  const after = structuredClone(before);
  after.estates[1].levels = 5;
  after.estates.splice(0, 1);
  expect(changesBetween(before, after).owners).toEqual([]);
});
