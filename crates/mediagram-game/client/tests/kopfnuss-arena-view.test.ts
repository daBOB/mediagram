import { expect, test } from "bun:test";
import { arenaView } from "../src/tv/kopfnuss/arena-view";
import type { KopfnussScene } from "../src/protocol/KopfnussScene";
import type { PlayerInfo } from "../src/protocol/PlayerInfo";

const players = [
  { id: 7, name: "Fox", color: "#fff", avatar: "fox", score: 0, connected: true, leader: true },
  { id: 3, name: "Panda", color: "#000", avatar: "panda", score: 0, connected: true, leader: false },
] satisfies PlayerInfo[];
const scene = (stage: unknown) => ({ kind: "kopfnuss", round: 1, rounds: 3, stage }) as KopfnussScene;

test("arena selects the reveal set during quiz reveal", () => {
  const view = arenaView(scene({ stage: "question", quiz: { stage: { stage: "reveal" } } }), players);
  expect(view.kind).toBe("reveal");
});

test("arena keeps player ids and maps climb steps", () => {
  const view = arenaView(scene({ stage: "climb", climbers: [{ player: 7, step: 8 }, { player: 3, step: 2 }] }), players);
  expect(view.kind).toBe("climb");
  expect(view.players.map((p) => [p.id, p.step])).toEqual([[7, 8], [3, 2]]);
});

test("arena passes the fact winner to the winner scene", () => {
  const view = arenaView(scene({ stage: "fact", winner: 3 }), players);
  expect(view.kind).toBe("fact");
  expect(view.winner).toBe(3);
});
