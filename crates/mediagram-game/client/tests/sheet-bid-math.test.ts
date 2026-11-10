// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { bidShare, clampBid, startBid } from "../src/phone/sheet-bid-math";

const range = (value: number, max: number, bank = 0) => ({ value, bank, max, step: 100, placed: null });

test("a bid starts at the bank's price, in whole hundreds and within the bidder's cash", () => {
  expect(startBid(range(3_700, 8_400, 1_850))).toBe(1_800);
  expect(startBid(range(3_700, 1_200, 1_850))).toBe(1_200);
});

test("a bid stays between one step and the bidder's cash", () => {
  const r = range(3_700, 2_400);
  expect(clampBid(r, -500)).toBe(100);
  expect(clampBid(r, 2_450)).toBe(2_400);
  expect(clampBid(r, 99_000)).toBe(2_400);
  expect(bidShare(r, 100)).toBe(2_400);
  expect(bidShare(r, 25)).toBe(900);
});
