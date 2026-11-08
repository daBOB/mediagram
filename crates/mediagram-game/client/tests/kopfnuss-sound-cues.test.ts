// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { HIT_STEP_MS, newSoundState, soundsDue } from "../src/tv/kopfnuss/kopfnuss-sound-cues";
import type { KopfnussScene } from "../src/protocol/KopfnussScene";
import type { KopfnussStage } from "../src/protocol/KopfnussStage";

const scene = (stage: KopfnussStage, round = 1): KopfnussScene => ({ round, rounds: 7, stage });
const pick = (done: number[]) => scene({ stage: "pick", categories: ["A", "B", "C"], done, closes_at: 99_000 });
const question = (quizStage: object, q = 1) =>
  scene({ stage: "question", category: "A", tricks: [], quiz: { round: q, rounds: 3, stage: quizStage } } as unknown as KopfnussStage);
const open = { stage: "open", question: {}, opens_at: 10_000, closes_at: 22_000, answered: [] };
const reveal = { stage: "reveal", question: {}, correct: 0, results: [], ends_at: 30_000 };
const climb = (steps: [number, number][]) =>
  scene({ stage: "climb", steps: 10, climbers: steps.map(([player, step]) => ({ player, step })), ends_at: 99_000 }, 0);
const names = (cues: { sound: string }[]) => cues.map((c) => c.sound);

test("a new round chimes once, and each player finishing their picks clicks once", () => {
  const s = newSoundState();
  expect(names(soundsDue(s, pick([]), 1000))).toEqual(["round"]);
  expect(names(soundsDue(s, pick([]), 1100))).toEqual([]);
  expect(names(soundsDue(s, pick([2]), 1200))).toEqual(["pickDone"]);
  expect(names(soundsDue(s, pick([2, 5]), 1300))).toEqual(["pickDone"]);
  expect(names(soundsDue(s, pick([2, 5]), 1400))).toEqual([]);
});

test("a TV that first sees the picks half done does not click for those already done", () => {
  const s = newSoundState();
  expect(names(soundsDue(s, pick([1, 2]), 1000))).toEqual(["round"]);
});

test("the chosen category sounds, then each trick lands in step with the TV's animation", () => {
  const s = newSoundState();
  const hits = [
    { from: 0, to: 1, trick: "spiegel", bounced: false },
    { from: 1, to: 0, trick: "kleber", bounced: true },
  ];
  const resolve = scene({ stage: "resolve", categories: ["A", "B", "C"], votes: [1, 1, 0], chosen: 0, hits, ends_at: 9000 } as KopfnussStage);
  // Cues are handed out a little ahead, so the first trick is scheduled with the category, for 100 ms later.
  const first = soundsDue(s, resolve, 5000);
  expect(first.map((c) => [c.sound, c.at])).toEqual([["chosen", 5000], ["zap", 5100]]);
  expect(names(soundsDue(s, resolve, 5000 + 300))).toEqual([]);
  const second = soundsDue(s, resolve, 5000 + 100 + HIT_STEP_MS - 50);
  expect(second.map((c) => [c.sound, c.at])).toEqual([["boing", 5000 + 100 + HIT_STEP_MS]]);
});

test("the buttons going live beep, the last three seconds tick, the reveal rings", () => {
  const s = newSoundState();
  expect(names(soundsDue(s, question({ ...open, stage: "intro" }), 9000))).toEqual([]);
  expect(names(soundsDue(s, question(open), 9950))).toEqual(["go"]);
  expect(names(soundsDue(s, question(open), 18_950))).toEqual(["tick"]);
  expect(names(soundsDue(s, question(open), 19_950))).toEqual(["tick"]);
  expect(names(soundsDue(s, question(reveal), 20_500))).toEqual(["reveal"]);
  expect(names(soundsDue(s, question(reveal), 20_950))).toEqual([], "no tick after an early reveal");
});

test("a TV joining after a sound's moment stays quiet instead of playing it late", () => {
  const s = newSoundState();
  expect(names(soundsDue(s, question(open), 12_000))).toEqual([]);
});

test("head-start steps are silent; every step climbed knocks once; the summit sounds once", () => {
  const s = newSoundState();
  expect(names(soundsDue(s, climb([[0, 3], [1, 2]]), 1000))).toEqual([]);
  expect(names(soundsDue(s, climb([[0, 4], [1, 2]]), 1100))).toEqual(["step"]);
  expect(names(soundsDue(s, climb([[0, 4], [1, 2]]), 1200))).toEqual([]);
  const fact = scene({ stage: "fact", winner: 0, text: "x", ends_at: 9000 }, 0);
  expect(names(soundsDue(s, fact, 2000))).toEqual(["summit"]);
  expect(names(soundsDue(s, fact, 2100))).toEqual([]);
  expect(names(soundsDue(s, scene({ stage: "final", order: [0, 1] }, 0), 3000))).toEqual([]);
});
