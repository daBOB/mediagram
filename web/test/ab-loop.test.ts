/** Covers `ab-loop`: marking out a stretch, and staying inside it. */

import { describe, expect, test } from "bun:test";
import { isLooping, loopBack, loopLabel, markLoop, NO_LOOP } from "../public/lib/ab-loop.js";

const clock = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;

describe("marking one out", () => {
  test("a then b", () => {
    const one = markLoop(NO_LOOP, "from", 90);
    const both = markLoop(one, "to", 165);
    expect(both).toEqual({ from: 90, to: 165 });
    expect(isLooping(both)).toBe(true);
  });

  test("b before a is the same loop, not an error", () => {
    // Marking the end of a passage first says something perfectly clear.
    const one = markLoop(NO_LOOP, "to", 165);
    const both = markLoop(one, "from", 90);
    expect(both).toEqual({ from: 90, to: 165 });
  });

  test("a again clears it, which is the only way out", () => {
    const both = markLoop(markLoop(NO_LOOP, "from", 90), "to", 165);
    expect(markLoop(both, "from", 200)).toEqual(NO_LOOP);
  });

  test("half a loop is not a loop", () => {
    expect(isLooping(markLoop(NO_LOOP, "from", 90))).toBe(false);
    expect(isLooping(NO_LOOP)).toBe(false);
  });

  test("and neither is a loop shorter than a second", () => {
    // Below that the seek back lands inside its own gap and the picture never
    // settles; it is also what a double-tap of `b` would otherwise produce.
    const tiny = markLoop(markLoop(NO_LOOP, "from", 90), "to", 90.2);
    expect(isLooping(tiny)).toBe(false);
  });

  test("a position that is not one changes nothing", () => {
    for (const at of [undefined, null, Number.NaN, -5, "soon"]) {
      expect(markLoop(NO_LOOP, "from", at as never)).toEqual(NO_LOOP);
    }
  });
});

describe("staying inside it", () => {
  const loop = { from: 90, to: 165 };

  test("running past the end goes back to the start", () => {
    expect(loopBack(loop, 165)).toBe(90);
    expect(loopBack(loop, 170)).toBe(90);
  });

  test("inside it, nothing happens", () => {
    // Called about four times a second, so the common answer has to be cheap.
    expect(loopBack(loop, 90)).toBeNull();
    expect(loopBack(loop, 120)).toBeNull();
  });

  test("seeking back out of it sends the viewer forward to the start", () => {
    // They asked for this passage, not for whatever precedes it.
    expect(loopBack(loop, 30)).toBe(90);
  });

  test("with no loop set, never", () => {
    expect(loopBack(NO_LOOP, 500)).toBeNull();
    expect(loopBack({ from: 90, to: null }, 500)).toBeNull();
  });
});

describe("what it says on screen", () => {
  test("both ends once both are set", () => {
    expect(loopLabel({ from: 90, to: 165 }, clock)).toBe("1:30 – 2:45");
  });

  test("and an ellipsis for the end still being waited for", () => {
    expect(loopLabel({ from: 90, to: null }, clock)).toBe("1:30 – …");
  });

  test("nothing at all when there is no loop", () => {
    expect(loopLabel(NO_LOOP, clock)).toBe("");
  });
});
