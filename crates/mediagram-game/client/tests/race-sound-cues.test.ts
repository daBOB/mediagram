// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { RaceMove } from "../src/protocol/RaceMove";
import type { RaceScene } from "../src/protocol/RaceScene";
import type { RaceStage } from "../src/protocol/RaceStage";
import { stageCues } from "../src/tv/race/race-sound-cues";

const scene = (stage: RaceStage): RaceScene => ({
  seats: [
    { player: 1, corner: 0, pieces: [null, null, null, null] },
    { player: 2, corner: 2, pieces: [null, null, null, null] },
  ],
  stage,
  die: null,
});
const moving = (mv: RaceMove): RaceStage => ({ stage: "moving", player: 1, value: 3, mv, started_at: 10_000, hop_ms: 280 });
const sounds = (stage: RaceStage) => stageCues(scene(stage), 0)!.cues.map((c) => `${c.sound}@${c.at}`);

test("the die clatters as it first touches the table", () => {
  const cues = stageCues(scene({ stage: "rolled", player: 1, value: 4, rolled_at: 5000, lands_at: 6150, moves: [] }), 0);
  expect(cues).toEqual({ key: "rolled 5000", cues: [{ sound: "die", at: 5000 + 1150 * 0.6 - 70 }] });
});

test("a piece coming out knocks once, on the start field", () => {
  expect(sounds(moving({ piece: 0, from: null, to: 0, captures: null }))).toEqual(["knock@10280"]);
});

test("a capture thunks at the hit and knocks again when the piece lands home", () => {
  expect(sounds(moving({ piece: 0, from: 5, to: 8, captures: { player: 2, piece: 1 } }))).toEqual([
    "knock@10280", "knock@10560", "knock@10840", "thunk@10840", "scream@10840", "knock@11440",
  ]);
});

test("only a move into the goal cheers, not one inside it", () => {
  expect(sounds(moving({ piece: 0, from: 38, to: 41, captures: null }))).toContain("goalJingle@10840");
  expect(sounds(moving({ piece: 0, from: 40, to: 42, captures: null }))).toEqual(["knock@10280", "knock@10560"]);
});

test("waiting for a roll is silent; the winner's jingle plays at once", () => {
  expect(stageCues(scene({ stage: "roll", player: 1, again: false }), 0)).toBeNull();
  expect(stageCues(scene({ stage: "final", winner: 2, ranking: [2, 1] }), 777)).toEqual({
    key: "final 2", cues: [{ sound: "winnerJingle", at: 777 }],
  });
});
