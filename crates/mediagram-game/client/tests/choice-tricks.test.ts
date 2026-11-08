// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { HOLD_MS, REVEAL_MS, SWAP_MS, holdDone, revealed, slotOrder } from "../src/phone/choice-tricks";

const isPermutation = (order: number[], n: number) => [...order].sort((a, b) => a - b).join() === [...Array(n).keys()].join();

test("buttons stay in place until the first swap", () => {
  expect(slotOrder(4, 1000, 1000)).toEqual([0, 1, 2, 3]);
  expect(slotOrder(4, 1000, 1000 + SWAP_MS - 1)).toEqual([0, 1, 2, 3]);
});

test("the order changes at the first swap and is the same for the same inputs", () => {
  const swapped = slotOrder(4, 1000, 1000 + SWAP_MS);
  expect(swapped).not.toEqual([0, 1, 2, 3]);
  expect(slotOrder(4, 1000, 1000 + SWAP_MS)).toEqual(swapped);
});

test("every swap is a permutation", () => {
  for (let k = 0; k < 40; k++) {
    for (const n of [2, 3, 4]) expect(isPermutation(slotOrder(n, 777, 777 + k * SWAP_MS), n)).toBe(true);
  }
});

test("consecutive swaps differ", () => {
  for (let k = 1; k < 20; k++) {
    expect(slotOrder(4, 50, 50 + k * SWAP_MS)).not.toEqual(slotOrder(4, 50, 50 + (k - 1) * SWAP_MS));
  }
});

test("letters trickle in over the reveal time", () => {
  expect(revealed("Elbe", 0, 0)).toBe("");
  expect(revealed("Elbe", 0, REVEAL_MS / 2)).toBe("El");
  expect(revealed("Elbe", 0, REVEAL_MS)).toBe("Elbe");
  expect(revealed("Elbe", 0, REVEAL_MS * 3)).toBe("Elbe");
  expect(revealed("Elbe", 500, 0)).toBe("", "before the buttons open");
});

test("a hold counts after a full second, not before", () => {
  expect(holdDone(1000, 1000 + HOLD_MS - 1)).toBe(false);
  expect(holdDone(1000, 1000 + HOLD_MS)).toBe(true);
});
