// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { Card } from "../src/protocol/Card";
import type { CardsEvent } from "../src/protocol/CardsEvent";
import type { CardsScene } from "../src/protocol/CardsScene";
import { DRAW_MS, DRAW_STAGGER_MS, PLAY_MS } from "../src/tv/cards/cards-motion";
import { cardsCues } from "../src/tv/cards/cards-sound-cues";
import { cueStart, smoothLag } from "../src/tv/table/tv-sound";

test("a cue starts early by the output's lag, so it is heard when its frame is on the screen two vsyncs later", () => {
  // half a second ahead on the host clock, 300 ms of output lag, 33 ms of screen delay
  expect(cueStart(1500, 1000, 10, 300)).toBeCloseTo(10.233, 9);
});

test("a cue just past plays at once; one more than 30 ms past is skipped", () => {
  expect(cueStart(1000 - 33 + 300 - 20, 1000, 10, 300)).toBe(10);
  expect(cueStart(1000 - 33 + 300 - 31, 1000, 10, 300)).toBeNull();
});

test("the output lag takes its first reading whole, then moves 5 % of the way to each new one", () => {
  expect(smoothLag(NaN, 380)).toBe(380);
  expect(smoothLag(400, 300)).toBe(395);
});

const card: Card = { suit: "hearts", rank: "seven" };
const tableWith = (events: CardsEvent[]) => ({ events }) as CardsScene;

test("the table knocks where each played card lands and once per drawn card, each event once", () => {
  const heard = new Set<number>();
  const played: CardsEvent = { id: 1, at: 1000, player: 0, kind: { kind: "play", card, pile: 0 } };
  const drawn: CardsEvent = { id: 2, at: 2000, player: 1, kind: { kind: "draw", count: 3 } };
  const shuffled: CardsEvent = { id: 3, at: 2500, player: 1, kind: { kind: "reshuffle" } };
  expect(cardsCues(tableWith([played]), heard)).toEqual({ key: "events 1", cues: [{ sound: "knock", at: 1000 + PLAY_MS }] });
  expect(cardsCues(tableWith([played, drawn, shuffled]), heard)).toEqual({
    key: "events 2,3",
    cues: [0, 1, 2].map((j) => ({ sound: "knock", at: 2000 + j * DRAW_STAGGER_MS + DRAW_MS })),
  });
  expect(cardsCues(tableWith([played, drawn, shuffled]), heard)).toBeNull();
});
