/** Covers `framing`: how the picture sits in the window. */

import { describe, expect, test } from "bun:test";
import {
  DEFAULT_FRAMING,
  framingBox,
  framingLabel,
  framingStyle,
  framings,
  nextFraming,
} from "../public/lib/framing.js";

describe("the cycle", () => {
  test("walks every framing and comes back", () => {
    const seen = [DEFAULT_FRAMING];
    for (let i = 0; i < framings().length - 1; i++) seen.push(nextFraming(seen.at(-1)!));
    expect(seen).toEqual(["fit", "fill", "16:9", "4:3"]);
    expect(nextFraming(seen.at(-1)!)).toBe(DEFAULT_FRAMING);
  });

  test("starts from the beginning when it is given nonsense", () => {
    // A viewer pressing `z` wants something to happen.
    for (const name of [undefined, null, "", "sideways"]) {
      expect(nextFraming(name as never)).toBe("fill");
    }
  });
});

describe("what each one does to the element", () => {
  test("fit letterboxes, which is the honest default", () => {
    expect(framingStyle("fit")).toEqual({ objectFit: "contain", aspectRatio: "" });
    expect(DEFAULT_FRAMING).toBe("fit");
  });

  test("fill crops to the window", () => {
    expect(framingStyle("fill")).toEqual({ objectFit: "cover", aspectRatio: "" });
  });

  test("a named ratio crops to that shape", () => {
    // For a film mastered with its bars burnt in, which is a correctly
    // displayed picture of a letterbox and no less black for it.
    expect(framingStyle("16:9").aspectRatio).toBe(String(16 / 9));
    expect(framingStyle("4:3").aspectRatio).toBe(String(4 / 3));
  });

  test("an empty ratio, not `auto` — the empty string is what removes it", () => {
    expect(framingStyle("fit").aspectRatio).toBe("");
  });

  test("and anything unknown falls back to fit rather than to nothing", () => {
    expect(framingStyle("sideways")).toEqual(framingStyle("fit"));
    expect(framingLabel("sideways")).toBe("Fit");
  });
});

describe("the window a named ratio crops into", () => {
  // Wider than 16:9, so a forced ratio's window never coincides with the
  // stage's own shape by accident — the same stage `FramingTest.kt` (the
  // Android port) uses, so a number here can be checked against one there.
  const stageWidth = 2000;
  const stageHeight = 900;

  test("fit and fill fill the stage exactly — object-fit alone is enough", () => {
    expect(framingBox("fit", stageWidth, stageHeight)).toBeNull();
    expect(framingBox("fill", stageWidth, stageHeight)).toBeNull();
  });

  // The stage (2000x900, ~2.22:1) is wider than both named ratios, so both
  // windows below are height-matched to it and pillarboxed left/right —
  // never letterboxed top/bottom, which only a stage narrower than the
  // ratio would produce.
  test("16:9 fits a 16:9 window inside the stage, centred", () => {
    const box = framingBox("16:9", stageWidth, stageHeight);
    const width = stageHeight * (16 / 9);
    expect(box).toEqual({ width, height: stageHeight, left: (stageWidth - width) / 2, top: 0 });
    // Never distorted: the window this crops into keeps the ratio asked for.
    expect(box!.width / box!.height).toBeCloseTo(16 / 9);
  });

  test("4:3 fits a narrower window still, pillarboxed further in", () => {
    const box = framingBox("4:3", stageWidth, stageHeight);
    const width = stageHeight * (4 / 3);
    expect(box).toEqual({ width, height: stageHeight, left: (stageWidth - width) / 2, top: 0 });
    expect(box!.width).toBeLessThan(stageWidth);
  });

  test("an unmeasured stage (nothing laid out yet) crops into nothing", () => {
    expect(framingBox("16:9", 0, 0)).toBeNull();
  });

  test("anything unknown is fit, which has no window either", () => {
    expect(framingBox("sideways", stageWidth, stageHeight)).toBeNull();
  });
});
