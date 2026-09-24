/** Covers `framing`: how the picture sits in the window. */

import { describe, expect, test } from "bun:test";
import {
  DEFAULT_FRAMING,
  framingLabel,
  framingStyle,
  framings,
  nextFraming,
} from "../public/lib/playback/framing.js";

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
