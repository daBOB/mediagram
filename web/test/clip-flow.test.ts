/** Covers `clip-flow`: where a clip starts, and what the panel does about a job. */

import { describe, expect, test } from "bun:test";
import { clipProblem, nextClipStep, presetAround } from "../public/lib/playback/clip-flow.js";

const job = (state: string, id = "a") => ({ id, state, progress: 0.5 });
const seen = (...ids: string[]) => new Set(ids);

describe("presetAround", () => {
  test("clamps at zero and at the runtime", () => {
    expect(presetAround(100, 1000)).toEqual({ from: 85, to: 115 });
    expect(presetAround(5, 1000)).toEqual({ from: 0, to: 20 });
    expect(presetAround(995, 1000)).toEqual({ from: 980, to: 1000 });
    expect(presetAround(100, null)).toEqual({ from: 85, to: 115 });
  });
});

describe("nextClipStep", () => {
  test("downloads once when done and visible", () => {
    expect(nextClipStep(job("done"), { visible: true, downloaded: seen() })).toBe("download");
    expect(nextClipStep(job("done"), { visible: true, downloaded: seen("a") })).toBe("idle");
  });

  test("waits while hidden and polls while running", () => {
    expect(nextClipStep(job("running"), { visible: true, downloaded: seen() })).toBe("poll");
    expect(nextClipStep(job("running"), { visible: false, downloaded: seen() })).toBe("wait");
    expect(nextClipStep(job("done"), { visible: false, downloaded: seen() })).toBe("wait");
  });

  test("a failed job is idle and shows its error", () => {
    const failed = { ...job("failed"), error: "No such audio track" };
    expect(nextClipStep(failed, { visible: true, downloaded: seen() })).toBe("idle");
    expect(nextClipStep(null, { visible: true, downloaded: seen() })).toBe("idle");
  });
});

describe("clipProblem", () => {
  test("over three minutes cannot be exported", () => {
    expect(clipProblem(0, 180)).toBeNull();
    expect(clipProblem(0, 181)).toBe("Clips can be up to 3 minutes");
    expect(clipProblem(10, 10.5)).not.toBeNull();
  });

  test("an end still being picked cannot be exported", () => {
    expect(clipProblem(null, null)).toBe("Seek, then set Start and End");
    expect(clipProblem(10, null)).toBe("Seek, then set Start and End");
    expect(clipProblem(10.25, 40.5)).toBeNull();
  });
});
