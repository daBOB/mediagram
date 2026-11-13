import { expect, test } from "bun:test";
import { isCrowded } from "../src/tv/lobby/TablePanel";

test("a note line under the seats makes the table crowded one seat sooner than two", () => {
  expect([0, 2, 3, 4, 5].map((n) => isCrowded(n, false))).toEqual([false, false, false, false, true]);
  expect([0, 2, 3, 4, 5].map((n) => isCrowded(n, true))).toEqual([false, false, true, true, true]);
});
