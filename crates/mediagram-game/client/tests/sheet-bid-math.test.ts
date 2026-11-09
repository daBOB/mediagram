// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { bidShare, clampBid, startBid } from "../src/phone/sheet-bid-math";

const range = (value: number, max: number) => ({ value, max, step: 100, placed: null });

test("a bid starts at half the value, in whole hundreds", () => {
  expect(startBid(range(3_700, 8_400))).toBe(1_800);
});

test("a bid stays between one step and the bidder's cash", () => {
  const r = range(3_700, 2_400);
  expect(clampBid(r, -500)).toBe(100);
  expect(clampBid(r, 2_450)).toBe(2_400);
  expect(clampBid(r, 99_000)).toBe(2_400);
  expect(bidShare(r, 100)).toBe(2_400);
  expect(bidShare(r, 25)).toBe(900);
});
