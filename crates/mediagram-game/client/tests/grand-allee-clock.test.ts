// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { bankAt, coinCount, coinProgress, flight, hops, pawnPlace, sharing } from "../src/tv/grand-allee/grand-allee-clock";
import { COINS, PAWN } from "../src/tv/grand-allee/grand-allee-look";
import { sceneOf } from "./grand-allee-fixture";

const HOP = 280;
// player 0 throws a 4 from field 38 and would land on field 2, where player 1 stands (rules.rs walk: passing a pawn uses a step,
// only landing on one moves the walker on), so it goes on to 3, passing the Bank: five hops
const walking = (bank = true) =>
  sceneOf({
    seats: [0, 1, 2, 3].map((player) => ({ player, field: [3, 2, 10, 10][player], cash: 1000, worth: 1000, out: false })),
    turn: { stage: "walking", player: 0, value: 4, from: 38, to: 3, started_at: 1000, hop_ms: HOP, bank },
  });

test("a walk that would end on an occupied field hops on to the next free one", () => {
  const s = walking();
  expect(hops(38, 3, 40)).toBe(5);
  expect(pawnPlace(s, 0, 900)).toEqual({ u: 38, lift: 0 });
  const mid = pawnPlace(s, 0, 1000 + 2.5 * HOP);
  expect(mid.u).toBeCloseTo(40.5);
  expect(mid.lift).toBeCloseTo(PAWN.hop);
  expect(pawnPlace(s, 0, 1000 + 99 * HOP)).toEqual({ u: 43, lift: 0 });
  // everyone else stands on their field
  expect(pawnPlace(s, 1, 1500)).toEqual({ u: 2, lift: 0 });
});

test("the lap money shows on the hop that reaches the Bank, not as the walk starts", () => {
  expect(bankAt(walking())).toBe(1000 + 2 * HOP);
  expect(bankAt(walking(false))).toBeNull();
  // a walk from the Bank itself reaches it again only after a whole lap
  expect(bankAt(sceneOf({ turn: { stage: "walking", player: 0, value: 6, from: 0, to: 6, started_at: 0, hop_ms: HOP, bank: true } }))).toBe(6 * HOP);
});

test("pawns sharing a field each get a place; the walker and the broke stand alone", () => {
  expect(sharing(sceneOf({}, 4))).toEqual([0, 1, 2, 3].map((k) => ({ k, n: 4 })));
  const s = walking();
  s.seats[1].out = true;
  s.seats[1].field = 10;
  expect(sharing(s)).toEqual([{ k: 0, n: 1 }, { k: 0, n: 1 }, { k: 0, n: 2 }, { k: 1, n: 2 }]);
});

test("a die flies from rolled_at to lands_at", () => {
  expect(flight(1000, 2150, 900)).toBe(0);
  expect(flight(1000, 2150, 1575)).toBeCloseTo(0.5);
  expect(flight(1000, 2150, 5000)).toBe(1);
});

test("a guest's coins leave one after another and the last arrives at paid_at", () => {
  expect(coinCount(10)).toBe(3);
  expect(coinCount(1500)).toBe(10);
  expect(coinCount(999_999)).toBe(COINS.max);
  const n = coinCount(450), [lands, paid] = [1000, 1900];
  expect(coinProgress(0, n, lands, paid, lands - 1)).toBeNull();
  expect(coinProgress(0, n, lands, paid, lands)).toBe(0);
  expect(coinProgress(n - 1, n, lands, paid, lands)).toBeNull();
  expect(coinProgress(n - 1, n, lands, paid, paid)).toBeCloseTo(1);
  expect(coinProgress(n - 1, n, lands, paid, paid + 1)).toBeNull();
});
