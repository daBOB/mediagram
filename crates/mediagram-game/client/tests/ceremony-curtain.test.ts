// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { OPEN_S, openProgress } from "../src/tv/ceremony/ceremony-curtain";

test("the curtain is closed before it opens and fully open after OPEN_S", () => {
  expect(openProgress(-1)).toBe(0);
  expect(openProgress(0)).toBe(0);
  expect(openProgress(OPEN_S)).toBe(1);
  expect(openProgress(OPEN_S * 3)).toBe(1);
});

test("the curtain opens steadily, slow at both ends", () => {
  const steps = Array.from({ length: 41 }, (_, i) => openProgress((i / 40) * OPEN_S));
  for (let i = 1; i < steps.length; i++) expect(steps[i]).toBeGreaterThanOrEqual(steps[i - 1]);
  expect(openProgress(OPEN_S / 2)).toBeCloseTo(0.5, 5);
  // eased: the first and last tenth move less than a tenth of the way
  expect(openProgress(OPEN_S / 10)).toBeLessThan(0.1);
  expect(1 - openProgress((OPEN_S * 9) / 10)).toBeLessThan(0.1);
});
