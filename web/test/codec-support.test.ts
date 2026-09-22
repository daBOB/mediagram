/** Covers `codec-support`: which codecs this browser says it decodes. */

import { describe, expect, test } from "bun:test";
import { browserDecodes } from "../public/lib/codec-support.js";

const engine = (direct: string, streamed: boolean) => ({
  canPlayType: () => direct,
  isTypeSupported: () => streamed,
});

describe("what a browser decodes", () => {
  test("HEVC counts when both direct play and MSE accept it", () => {
    expect(browserDecodes(engine("probably", true))).toEqual(["hevc"]);
  });

  test("one of the two is not enough", () => {
    // Direct play without MSE would promise a conversion hls.js then refuses,
    // and the other way round a file the element refuses.
    expect(browserDecodes(engine("probably", false))).toEqual([]);
    expect(browserDecodes(engine("", true))).toEqual([]);
  });

  test("a browser that only says maybe is not taken at its word", () => {
    expect(browserDecodes(engine("maybe", true))).toEqual([]);
  });

  test("an engine that throws, or is missing, decodes nothing extra", () => {
    const throwing = {
      canPlayType: () => {
        throw new Error("no");
      },
    };
    expect(browserDecodes(throwing)).toEqual([]);
    expect(browserDecodes({})).toEqual([]);
  });
});
