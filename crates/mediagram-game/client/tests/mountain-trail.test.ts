// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { SUMMIT, TRAIL, pointAt, spots } from "../src/tv/kopfnuss/mountain-trail";

test("the trail starts at the foot and ends on the summit", () => {
  expect(pointAt(0)).toEqual(TRAIL[0]);
  expect(pointAt(1)).toEqual(SUMMIT);
  expect(pointAt(-1)).toEqual(TRAIL[0]);
  expect(pointAt(2)).toEqual(SUMMIT);
});

test("every step along the trail is higher than the one before", () => {
  // Screen y grows downward, so higher means a smaller y.
  for (let i = 1; i <= 10; i++) expect(pointAt(i / 10).y).toBeLessThan(pointAt((i - 1) / 10).y);
});

test("climbers on different steps stand where their step is", () => {
  const at = spots([{ player: 1, step: 0 }, { player: 2, step: 10 }], 10);
  expect(at.get(1)).toEqual(pointAt(0));
  expect(at.get(2)).toEqual(SUMMIT);
});

test("climbers sharing a step stand side by side, centred on it", () => {
  const at = spots([{ player: 1, step: 3 }, { player: 2, step: 3 }, { player: 3, step: 3 }], 10);
  const [a, b, c] = [at.get(1)!, at.get(2)!, at.get(3)!];
  const spot = pointAt(0.3);
  expect([a.y, b.y, c.y]).toEqual([spot.y, spot.y, spot.y]);
  expect(a.x).toBeLessThan(b.x);
  expect(b.x).toBeLessThan(c.x);
  expect(b.x).toBeCloseTo(spot.x);
  expect(spot.x - a.x).toBeCloseTo(c.x - spot.x);
});

test("with one step per corner, every step stands on a corner of the trail", () => {
  for (let i = 0; i < TRAIL.length; i++) expect(pointAt(i / (TRAIL.length - 1))).toEqual(TRAIL[i]);
});
