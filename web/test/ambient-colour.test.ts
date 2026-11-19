import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { glowOf, strongestColourOf } from "../public/lib/ambient-colour.js";

/** Pixels as the canvas hands them over: four bytes each. */
const rgba = (...runs: Array<[number, number, number, number]>) =>
  Uint8ClampedArray.from(runs.flatMap(([r, g, b, n]) => Array.from({ length: n }, () => [r, g, b, 255]).flat()));

// Expected values come from a single-precision port of AmbientGlow.kt's
// strongestColourOf/glowOf over androidx ColorUtils, run on the same pixels.
const posters = {
  vivid: { px: rgba([220, 40, 30, 6], [240, 80, 60, 4], [30, 60, 200, 3], [10, 10, 10, 5], [250, 250, 245, 2]), strongest: [227, 55, 41], glow: [237, 47, 31] },
  dark: { px: rgba([20, 10, 40, 8], [30, 20, 60, 4], [5, 5, 5, 4]), strongest: [23, 13, 47], glow: [71, 20, 195] },
  grey: { px: rgba([90, 90, 90, 5], [180, 180, 180, 5], [20, 20, 20, 3]), strongest: [108, 108, 108], glow: [117, 99, 99] },
  tealBesideRed: { px: rgba([20, 160, 170, 5], [200, 30, 30, 3], [15, 15, 15, 4]), strongest: [20, 160, 170], glow: [16, 186, 198] },
};

for (const [name, { px, strongest, glow }] of Object.entries(posters)) {
  test(`a ${name} poster gets the strongest colour and glow Android works out`, () => {
    expect(strongestColourOf(px)).toEqual(strongest);
    expect(glowOf(strongestColourOf(px))).toEqual(glow);
  });
}

test("pure red lifts to a saturated glow (worked by hand)", () => {
  expect(glowOf(strongestColourOf(rgba([255, 0, 0, 10])))).toEqual([236, 19, 19]);
});

test("a poster with no pixels is grey", () => {
  expect(strongestColourOf(new Uint8ClampedArray(0))).toEqual([128, 128, 128]);
});

test("choosing Ambient is stored and applied to the page by the boot script", async () => {
  const stored = new Map<string, string>();
  const root = { dataset: {} as Record<string, string> };
  const listeners = new Map<string, () => void>();
  const env = {
    document: { documentElement: root },
    matchMedia: () => ({ matches: false, addEventListener() {} }),
    localStorage: { getItem: (k: string) => stored.get(k) ?? null, setItem: (k: string, v: string) => void stored.set(k, v) },
    addEventListener: (type: string, fn: () => void) => void listeners.set(type, fn),
  };
  const boot = readFileSync(join(import.meta.dir, "../public/lib/appearance-boot.js"), "utf8");
  new Function(...Object.keys(env), boot)(...Object.values(env));
  expect(root.dataset.backdrop).toBe("default");

  stored.set("mediagram.backdrop", "ambient");
  listeners.get("mediagram:appearance")!();
  expect(root.dataset.backdrop).toBe("ambient");
});
