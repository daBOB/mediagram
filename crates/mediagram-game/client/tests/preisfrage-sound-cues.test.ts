// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { newSoundState } from "../src/tv/kopfnuss/kopfnuss-sound-cues";
import { preisfrageSoundsDue } from "../src/tv/preisfrage/preisfrage-sound-cues";
import type { PreisfrageScene } from "../src/protocol/PreisfrageScene";
import type { PreisfrageStage } from "../src/protocol/PreisfrageStage";

const scene = (stage: PreisfrageStage, round = 1): PreisfrageScene => ({ round, rounds: 6, article: { asin: "B0", name: "Toaster", photo: "/img/article/B0" }, stage });
const guessing = (guessed: number[], round = 1) => scene({ stage: "guessing", opens_at: 10_000, closes_at: 30_000, guessed }, round);
const reveal = (round = 1) => scene({ stage: "reveal", cents: 1999, checked: "12.11.", rows: [], ends_at: 40_000 }, round);
const names = (cues: { sound: string }[]) => cues.map((c) => c.sound);

test("the article chimes as it appears, the keypads opening beep, and each guess clicks once", () => {
  const s = newSoundState();
  expect(names(preisfrageSoundsDue(s, guessing([]), 7000))).toEqual(["round"]);
  expect(names(preisfrageSoundsDue(s, guessing([]), 8000))).toEqual([], "no countdown ticks during the intro");
  expect(names(preisfrageSoundsDue(s, guessing([]), 9950))).toEqual(["go"]);
  expect(names(preisfrageSoundsDue(s, guessing([2]), 12_000))).toEqual(["pickDone"]);
  expect(names(preisfrageSoundsDue(s, guessing([2, 5]), 12_100))).toEqual(["pickDone"]);
  expect(names(preisfrageSoundsDue(s, guessing([2, 5]), 12_200))).toEqual([]);
});

test("the last three seconds tick, then the price rings once", () => {
  const s = newSoundState();
  preisfrageSoundsDue(s, guessing([]), 9950);
  expect(names(preisfrageSoundsDue(s, guessing([]), 26_950))).toEqual(["tick"]);
  expect(names(preisfrageSoundsDue(s, guessing([]), 27_950))).toEqual(["tick"]);
  expect(names(preisfrageSoundsDue(s, guessing([]), 28_950))).toEqual(["tick"]);
  expect(names(preisfrageSoundsDue(s, reveal(), 30_100))).toEqual(["reveal"]);
  expect(names(preisfrageSoundsDue(s, reveal(), 31_000))).toEqual([]);
});

test("everyone guessing early goes straight to the price: no ticks after it", () => {
  const s = newSoundState();
  preisfrageSoundsDue(s, guessing([]), 9950);
  expect(names(preisfrageSoundsDue(s, reveal(), 15_000))).toEqual(["reveal"]);
  expect(names(preisfrageSoundsDue(s, reveal(), 26_950))).toEqual([]);
});

test("the next article chimes again; a TV joining mid-guess does not click for guesses already in", () => {
  const s = newSoundState();
  expect(names(preisfrageSoundsDue(s, guessing([1, 2], 2), 15_000))).toEqual(["round"]);
  expect(names(preisfrageSoundsDue(s, guessing([1, 2, 3], 2), 15_100))).toEqual(["pickDone"]);
  expect(names(preisfrageSoundsDue(s, guessing([], 3), 50_000))).toEqual(["round"]);
});

test("the final ranking is left to the ceremony's jingle", () => {
  const s = newSoundState();
  expect(names(preisfrageSoundsDue(s, scene({ stage: "final", standings: [] }, 6), 1000))).toEqual([]);
});
