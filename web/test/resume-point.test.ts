/** Covers `resume-point`: which recorded positions are worth going back to. */

import { describe, expect, test } from "bun:test";
import {
  isFinished,
  resumeAt,
  trustedRuntime,
  watchedFraction,
} from "../public/lib/resume-point.js";

const at = (seconds: number, duration: number | null = 2400) => ({ at: seconds, duration });

describe("resuming", () => {
  test("a position in the middle is one to go back to", () => {
    expect(resumeAt(at(1200))).toBe(1200);
  });

  test("the first half minute was not watching, it was looking", () => {
    expect(resumeAt(at(12))).toBeNull();
    expect(resumeAt(at(29))).toBeNull();
    expect(resumeAt(at(31))).toBe(31);
  });

  test("a long title keeps its last minute, not its last twelve", () => {
    // Five percent of four hours is twelve minutes. The tail is the smaller
    // of the two, so this is still very much unfinished.
    expect(isFinished(13_000, 14_400)).toBe(false);
    expect(isFinished(14_350, 14_400)).toBe(true);
  });

  test("the credits are not a place to resume", () => {
    // The tail of a 40-minute title is a minute: five percent would be two,
    // and nobody has finished an episode with two minutes left.
    expect(resumeAt(at(2340))).toBeNull();
    expect(resumeAt(at(2339))).toBe(2339);
  });

  /**
   * The band this library mostly holds: 84 titles between one and five
   * minutes, and three under a minute. A flat one-minute tail made the short
   * ones finished before they started and the rest finished seconds in — the
   * position deleted on every save, and never a place on the Continue shelf.
   */
  test("a short lesson is not finished the moment it starts", () => {
    // A two-minute lesson: the tail is six seconds, not sixty.
    expect(resumeAt(at(60, 120))).toBe(60);
    expect(resumeAt(at(113, 120))).toBe(113);
    expect(resumeAt(at(115, 120))).toBeNull();

    // The shortest titles in the library, at 37 and 57 seconds.
    expect(isFinished(20, 37)).toBe(false);
    expect(isFinished(36, 37)).toBe(true);
    expect(isFinished(1, 57)).toBe(false);
  });

  test("half a minute is a glance at a film and most of a short lesson", () => {
    // A film: the first half minute was opening it, not watching it.
    expect(resumeAt(at(29, 2400))).toBeNull();
    expect(resumeAt(at(31, 2400))).toBe(31);

    // A 64-second lesson: a tenth of it, not half of it.
    expect(resumeAt(at(5, 64))).toBeNull();
    expect(resumeAt(at(20, 64))).toBe(20);
  });

  test("a long title is finished by the last minute, not by a percentage", () => {
    // 95% of four hours is twelve minutes from the end, which is not finished.
    expect(resumeAt(at(13_000, 14_400))).toBe(13_000);
    expect(resumeAt(at(14_350, 14_400))).toBeNull();
  });

  test("with no runtime, a position is taken at face value", () => {
    // Nothing says it is the end, so it is offered rather than dropped.
    expect(resumeAt(at(9000, null))).toBe(9000);
    expect(isFinished(9000, Number.NaN)).toBe(false);
  });

  test("nothing recorded is nothing to resume", () => {
    expect(resumeAt(null)).toBeNull();
    expect(resumeAt(at(Number.NaN))).toBeNull();
  });
});

describe("how far through", () => {
  test("a fraction of the runtime", () => {
    expect(watchedFraction(at(1200))).toBe(0.5);
    expect(watchedFraction(at(2400))).toBe(1);
  });

  test("never past the ends, however odd the numbers", () => {
    expect(watchedFraction(at(9999))).toBe(1);
    expect(watchedFraction(at(-5))).toBe(0);
  });

  test("an unknown runtime draws no bar rather than a meaningless one", () => {
    expect(watchedFraction(at(600, null))).toBeNull();
    expect(watchedFraction(at(600, 0))).toBeNull();
    expect(watchedFraction(null)).toBeNull();
  });
});

describe("which runtime to believe", () => {
  test("the catalog's, whenever there is one", () => {
    expect(trustedRuntime({ catalogued: 2400, observed: 30, direct: true })).toBe(2400);
    expect(trustedRuntime({ catalogued: 2400, observed: 30, direct: false })).toBe(2400);
  });

  test("the browser's, but only when it has the whole file", () => {
    expect(trustedRuntime({ catalogued: null, observed: 1800, direct: true })).toBe(1800);
  });

  /**
   * The bug this exists to stop. A conversion's `video.duration` is the length
   * encoded so far, so it sits a few seconds ahead of the playhead for the
   * whole film — which reads as finished, deletes the position on every save,
   * and never looks wrong.
   */
  test("never a conversion's, which is only what has been encoded so far", () => {
    expect(trustedRuntime({ catalogued: null, observed: 1260, direct: false })).toBe(0);
    // And 0 is what stops everything downstream from drawing a conclusion.
    expect(isFinished(1200, trustedRuntime({ catalogued: null, observed: 1260, direct: false })))
      .toBe(false);
    expect(resumeAt({ at: 1200, duration: 0 })).toBe(1200);
  });

  test("nothing known anywhere is 0, not a guess", () => {
    expect(trustedRuntime({ catalogued: null, observed: Number.NaN, direct: true })).toBe(0);
    expect(trustedRuntime({ catalogued: 0, observed: 0, direct: true })).toBe(0);
    expect(trustedRuntime({ catalogued: undefined, observed: undefined, direct: false })).toBe(0);
  });
});
