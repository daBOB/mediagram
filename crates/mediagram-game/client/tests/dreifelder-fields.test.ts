// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { placePicks } from "../src/tv/dreifelder/dreifelder-fields";

const picks = [
  { player: 0, choice: 2 },
  { player: 1, choice: 0 },
  { player: 2, choice: null },
  { player: 3, choice: 2 },
  { player: 4, choice: null },
];

test("players in the round stand in their field, ghosts beside them", () => {
  const { fields } = placePicks(picks, new Set([0, 1, 2]), 3);
  expect(fields).toEqual([
    { alive: [1], ghosts: [] },
    { alive: [], ghosts: [] },
    { alive: [0], ghosts: [3] },
  ]);
});

test("a player in the round without a pick is listed apart; a ghost without one is not shown", () => {
  expect(placePicks(picks, new Set([0, 1, 2]), 3).unpicked).toEqual([2]);
});

test("three fields even when nobody picked", () => {
  expect(placePicks([], new Set(), 3).fields).toHaveLength(3);
});
