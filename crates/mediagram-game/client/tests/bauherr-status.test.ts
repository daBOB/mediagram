// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { BauherrScene } from "../src/protocol/BauherrScene";
import type { BauherrTurn } from "../src/protocol/BauherrTurn";
import type { PlayerInfo } from "../src/protocol/PlayerInfo";
import { describeTurn } from "../src/tv/bauherr/bauherr-status";

const players = [
  { id: 0, name: "Ana" },
  { id: 1, name: "Ben" },
] as PlayerInfo[];

const scene = (turn: BauherrTurn) =>
  ({
    board: { tiles: [], plots: [{ name: "Seeblick" }] },
    estates: [],
    entrances: [],
    seats: [],
    round: 1,
    rounds: 48,
    turn,
  }) as unknown as BauherrScene;

test("the die shows ellipsis while it flies and its value once it landed", () => {
  const turn: BauherrTurn = { stage: "rolled", player: 0, value: 4, rolled_at: 0, lands_at: 1000 };
  expect(describeTurn(scene(turn), players, 500)).toEqual({ text: "Ana würfelt …", die: "…" });
  expect(describeTurn(scene(turn), players, 1500)).toEqual({ text: "Ana würfelt eine 4.", die: "4" });
});

test("a guest payment names guest, owner, amount, nights and hotel", () => {
  const turn: BauherrTurn = { stage: "guest", player: 0, owner: 1, plot: 0, nights: 3, amount: 1200, rolled_at: 0, lands_at: 1000, paid_at: 1900 };
  const { text, die } = describeTurn(scene(turn), players, 2000);
  expect(die).toBe("3");
  expect(text).toContain("Ana zahlt Ben");
  expect(text).toContain("3 Nächte im Seeblick");
});
